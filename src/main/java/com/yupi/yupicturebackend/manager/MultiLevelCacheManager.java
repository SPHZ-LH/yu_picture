package com.yupi.yupicturebackend.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.yupi.yupicturebackend.model.dto.cache.CacheEvictResult;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.enums.CacheTypeEnum;
import com.yupi.yupicturebackend.model.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 多级缓存管理器：一级本地缓存（Caffeine） + 二级分布式缓存（Redis）。
 * <p>
 * 读路径：L1 -> L2 -> DB，逐层回源、逐层回填。
 * 写路径：按「先删 L2、再删 L1」的顺序失效（顺序理由见 docs/adr/0003）。
 * <p>
 * 设计上的两个刻意选择：
 * <ol>
 *   <li><b>不依赖任何 Service</b>。回源逻辑由调用方以 {@link Function} 传入，
 *       否则本类需要注入 Service、而 Service 又需要注入本类，形成循环依赖
 *       （Spring Boot 2.6+ 默认禁止循环引用，会直接启动失败）。</li>
 *   <li><b>使用独立的 ObjectMapper</b>，不复用 Spring MVC 那个 Bean。
 *       MVC 的 ObjectMapper 会把 Long 序列化成字符串（防前端精度丢失），
 *       缓存若复用它，将来调整 MVC 的 JSON 配置就会静默改变缓存存储格式，
 *       导致旧缓存反序列化失败。这里让缓存格式完全独立。</li>
 * </ol>
 * <p>
 * 降级：Redis 的任何读写失败都会被捕获并记日志，接口不会因此失败——
 * 读路径退化为 L1 -> DB，写路径只删 L1（docs/adr/0003）。
 *
 * @author SPHZ
 */
@Slf4j
@Component
public class MultiLevelCacheManager {

    /**
     * key 前缀，避免与其它业务的 key 冲突
     */
    private static final String PICTURE_KEY_PREFIX = "yupicture:picture:";
    private static final String USER_KEY_PREFIX = "yupicture:user:";

    /**
     * L2 过期时间。一致性由显式失效保证，TTL 只是安全网，所以取偏长的值。
     * L2 比 L1（10 分钟）长，因为它是回源前的最后一道缓存。
     */
    private static final Duration PICTURE_L2_TTL = Duration.ofMinutes(30);
    private static final Duration USER_L2_TTL = Duration.ofMinutes(30);

    /**
     * 空值哨兵的过期时间，必须短——避免把「暂时查不到」长期固化成「永远不存在」
     */
    private static final Duration NULL_SENTINEL_TTL = Duration.ofSeconds(60);

    /**
     * 空值哨兵：表示「这个东西确实不存在」。必须与真值可区分（docs/adr/0004）。
     */
    private static final String NULL_SENTINEL = "__NULL__";

    private final ObjectMapper cacheObjectMapper = new ObjectMapper()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Resource(name = "pictureLocalCache")
    private Cache<Long, Picture> pictureLocalCache;

    @Resource(name = "userLocalCache")
    private Cache<Long, UserVO> userLocalCache;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisCircuitBreaker redisCircuitBreaker;

    // ==================== 读路径：L1 -> L2 -> DB ====================

    /**
     * 读取图片，逐层回源并回填。
     * <p>
     * Caffeine 的 {@code get(key, loader)} 是**原子加载**：同一个 key 的并发未命中
     * 只会执行一次 loader。因此整条 L1 -> L2 -> DB 链路天然免疫缓存击穿，
     * 单实例下不需要任何分布式锁。
     *
     * @param id       图片 id
     * @param dbLoader 回源数据库的函数，由调用方提供（避免循环依赖）
     * @return 图片；不存在时返回 null
     */
    public Picture getPicture(Long id, Function<Long, Picture> dbLoader) {
        if (id == null) {
            return null;
        }
        return pictureLocalCache.get(id, key -> loadPicture(key, dbLoader));
    }

    /**
     * 读取作者视图，逐层回源并回填。
     *
     * @param id       用户 id
     * @param dbLoader 回源数据库的函数
     * @return 作者视图；不存在时返回 null
     */
    public UserVO getUser(Long id, Function<Long, UserVO> dbLoader) {
        if (id == null) {
            return null;
        }
        return userLocalCache.get(id, key -> loadUser(key, dbLoader));
    }

    private Picture loadPicture(Long id, Function<Long, Picture> dbLoader) {
        String key = pictureKey(id);
        String raw = readFromRedis(key);
        if (raw != null) {
            // 命中空值哨兵：L2 明确告知「不存在」，不再回源数据库，也不写入 L1
            if (NULL_SENTINEL.equals(raw)) {
                return null;
            }
            Picture cached = deserialize(raw, Picture.class, key);
            if (cached != null) {
                return cached;
            }
            // 反序列化失败已按未命中处理（deserialize 内部会删掉脏 key），继续走数据库
        }
        Picture fromDb = dbLoader.apply(id);
        if (fromDb == null) {
            // 不存在的图片：只在 L2 写哨兵拦住穿透，L1 保持无空值（docs/adr/0004）
            writeToRedis(key, NULL_SENTINEL, NULL_SENTINEL_TTL);
            return null;
        }
        // 回填 L2；L1 由 Caffeine 在 loader 返回后自动填充
        writeToRedis(key, serialize(fromDb), PICTURE_L2_TTL);
        return fromDb;
    }

    private UserVO loadUser(Long id, Function<Long, UserVO> dbLoader) {
        String key = userKey(id);
        String raw = readFromRedis(key);
        if (raw != null) {
            UserVO cached = deserialize(raw, UserVO.class, key);
            if (cached != null) {
                return cached;
            }
        }
        UserVO fromDb = dbLoader.apply(id);
        if (fromDb == null) {
            // 作者不存在属于数据异常，不做哨兵缓存
            // （docs/adr/0004 的空值哨兵只覆盖「图片不存在」）
            return null;
        }
        writeToRedis(key, serialize(fromDb), USER_L2_TTL);
        return fromDb;
    }

    // ==================== 写路径：先删 L2，再删 L1 ====================

    /**
     * 使图片缓存失效。
     * <p>
     * <b>顺序不可调换</b>：先删 L2、再删 L1。若反过来（先删 L1 再删 L2），
     * 两步之间的并发读会从 L2 拿到旧值并回填进 L1，使 L1 脏到 TTL 过期；
     * 当前顺序下最多只脏一次读。详见 docs/adr/0003。
     * <p>
     * 另外：本方法必须在**数据库写入成功且事务提交之后**调用。
     * 当前项目没有 @Transactional，updateById 返回时已自动提交，所以是安全的；
     * 将来若给写方法加事务，必须把失效移到提交之后。
     *
     * @return 各层实际清除情况；id 为空时返回 null
     */
    public CacheEvictResult evictPicture(Long id) {
        return evict(id, CacheTypeEnum.PICTURE, pictureKey(id), pictureLocalCache);
    }

    /**
     * 使用户缓存失效。顺序要求同 {@link #evictPicture(Long)}。
     *
     * @return 各层实际清除情况；id 为空时返回 null
     */
    public CacheEvictResult evictUser(Long id) {
        return evict(id, CacheTypeEnum.USER, userKey(id), userLocalCache);
    }

    /**
     * 两类缓存区共用的失效流程。
     * <p>
     * 注意熔断器的放行判断只在 {@link #deleteFromRedisInto} 内部做**一次**：
     * 半开状态下 {@code allowRequest()} 会消耗掉唯一的探测许可，
     * 若在别处重复调用，会导致「判定为已放行、实际却被跳过」的不一致。
     */
    private CacheEvictResult evict(Long id, CacheTypeEnum type, String redisKey, Cache<?, ?> localCache) {
        if (id == null) {
            return null;
        }
        CacheEvictResult result = new CacheEvictResult();
        result.setType(type.getValue());
        result.setId(id);
        // 顺序不可调换：先删 L2、再删 L1（docs/adr/0003）
        deleteFromRedisInto(redisKey, result);
        // 先探测再删，以免拿不到「本来是否存在」
        result.setLocalRemoved(localCache.getIfPresent(id) != null);
        localCache.invalidate(id);
        return result;
    }

    // ==================== 可观测性 ====================

    /**
     * 返回缓存运行状态快照：两层 L1 的命中率统计 + L2（Redis）熔断器状态。
     * <p>
     * 缓存构建时已开启 recordStats（见 CacheConfig）；将来若要持续观测，
     * 可把本方法接到监控端点或定时日志上。
     * <p>
     * 重点关注 {@code l2Circuit}：一旦出现 {@code open=true}，说明 Redis 正在故障，
     * 此时缓存已完全退化为直查数据库，数据库压力会明显上升。
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pictureLocal", describe(pictureLocalCache));
        stats.put("userLocal", describe(userLocalCache));
        Map<String, Object> circuit = new LinkedHashMap<>();
        circuit.put("open", redisCircuitBreaker.isOpen());
        circuit.put("consecutiveFailures", redisCircuitBreaker.getConsecutiveFailures());
        stats.put("l2Circuit", circuit);
        return stats;
    }

    private Map<String, Object> describe(Cache<?, ?> cache) {
        CacheStats cacheStats = cache.stats();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("hitCount", cacheStats.hitCount());
        item.put("missCount", cacheStats.missCount());
        item.put("hitRate", cacheStats.hitRate());
        item.put("evictionCount", cacheStats.evictionCount());
        item.put("size", cache.estimatedSize());
        return item;
    }

    // ==================== Redis 读写（熔断 + 降级） ====================
    // 所有 Redis 访问都必须先经过熔断器：熔断期间直接跳过 Redis，不再建立连接、
    // 不再等待超时，避免把「缓存故障」放大成「应用级雪崩」。

    private String readFromRedis(String key) {
        if (!redisCircuitBreaker.allowRequest()) {
            // 熔断中：按未命中处理。注意此时读路径会直接落到数据库，数据是**新鲜的**
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            redisCircuitBreaker.recordSuccess();
            return value;
        } catch (Exception e) {
            // 降级：Redis 不可用时当作未命中，继续走数据库，接口不失败
            redisCircuitBreaker.recordFailure();
            log.warn("[多级缓存] 读取 L2 失败，降级为直查数据库, key={}", key, e);
            return null;
        }
    }

    private void writeToRedis(String key, String value, Duration ttl) {
        if (value == null) {
            return;
        }
        if (!redisCircuitBreaker.allowRequest()) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(key, value, ttl);
            redisCircuitBreaker.recordSuccess();
        } catch (Exception e) {
            redisCircuitBreaker.recordFailure();
            log.warn("[多级缓存] 写入 L2 失败，已忽略, key={}", key, e);
        }
    }

    /**
     * 删除 L2 的 key，并把结果写进 {@code result}。
     * <p>
     * <b>已知副作用（Redis 故障期间）</b>：熔断或失败时本方法什么都不做，
     * 即「这次失效没有清掉 L2」。于是 Redis 恢复后，那条**旧值会重新生效**——
     * 下次读会从 L2 拿到它并回填 L1，直到它的 TTL 到期。
     * <p>
     * 这**不是**熔断引入的新问题：熔断之前，删除同样会失败（只是要多等 3 秒超时），
     * 结果完全一样。它本质上是「失效必须成功」这个前提在 Redis 故障时被打破。
     * 兜底手段有两条：L2 的 TTL，以及管理员的手动失效接口（POST /cache/evict）。
     */
    private void deleteFromRedisInto(String key, CacheEvictResult result) {
        if (!redisCircuitBreaker.allowRequest()) {
            // 熔断中：完全没有尝试访问 Redis。必须标记出来，
            // 否则调用方会把「因为熔断没清」误读成「L2 本来就没有这条数据」
            result.setRedisSkipped(true);
            return;
        }
        try {
            result.setRedisRemoved(Boolean.TRUE.equals(stringRedisTemplate.delete(key)));
            redisCircuitBreaker.recordSuccess();
        } catch (Exception e) {
            // 降级：删不掉 L2 时 L1 仍会失效，且 L2 的 TTL 会兜住，不影响接口
            redisCircuitBreaker.recordFailure();
            log.warn("[多级缓存] 删除 L2 失败，已忽略（L1 仍会失效，TTL 兜底）, key={}", key, e);
        }
    }

    // ==================== 序列化 ====================

    /**
     * 丢弃一个损坏的 L2 key。
     * <p>
     * 仅用于清理反序列化失败的脏数据，不参与业务失效，因此不需要返回明细。
     * 调用点必然在 {@link #readFromRedis} **成功之后**，此时熔断器处于闭合状态
     * （成功会重置状态），所以这里再调 {@code allowRequest()} 不会误消耗半开探测许可。
     */
    private void deleteFromRedis(String key) {
        if (!redisCircuitBreaker.allowRequest()) {
            return;
        }
        try {
            stringRedisTemplate.delete(key);
            redisCircuitBreaker.recordSuccess();
        } catch (Exception e) {
            redisCircuitBreaker.recordFailure();
            log.warn("[多级缓存] 丢弃损坏的 L2 key 失败, key={}", key, e);
        }
    }

    private String serialize(Object value) {
        try {
            return cacheObjectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("[多级缓存] 序列化失败，跳过 L2 写入, type={}", value.getClass().getName(), e);
            return null;
        }
    }

    private <T> T deserialize(String raw, Class<T> type, String key) {
        try {
            return cacheObjectMapper.readValue(raw, type);
        } catch (Exception e) {
            // 缓存内容损坏或类结构已变更：删掉脏数据并按未命中处理，避免持续报错
            log.warn("[多级缓存] L2 反序列化失败，已删除该 key 并按未命中处理, key={}", key, e);
            deleteFromRedis(key);
            return null;
        }
    }

    private String pictureKey(Long id) {
        return PICTURE_KEY_PREFIX + id;
    }

    private String userKey(Long id) {
        return USER_KEY_PREFIX + id;
    }
}
