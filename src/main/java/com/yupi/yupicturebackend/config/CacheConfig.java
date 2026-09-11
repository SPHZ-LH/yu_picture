package com.yupi.yupicturebackend.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yupi.yupicturebackend.model.entity.Picture;
import com.yupi.yupicturebackend.model.vo.UserVO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存的「一级缓存」（本地 Caffeine）配置。
 * <p>
 * 设计依据见 docs/adr/0001（图片与作者拆成两个独立缓存区）
 * 与 docs/adr/0003（失效顺序）、docs/adr/0004（L1 不含空值）。
 * <p>
 * TTL 的角色：本项目的缓存一致性由写入侧的**显式失效**保证（见 {@code *ServiceImpl}
 * 里 override 的写方法），TTL 只是应对「未来出现未知漏网写路径」的安全网，
 * 不是一致性保证。所以这里取 10 分钟这一偏长的值，用来换取更高的命中率。
 *
 * @author SPHZ
 */
@Configuration
public class CacheConfig {

    /**
     * 本地缓存容量。图片与用户分别 10,000 条
     */
    private static final long LOCAL_MAX_SIZE = 10_000L;

    /**
     * 本地缓存过期时间（分钟）——安全网，不是一致性保证
     */
    private static final long LOCAL_EXPIRE_MINUTES = 10L;

    /**
     * 图片本地缓存：图片 id -> 图片自身数据
     * <p>
     * 注意值类型不含空值语义：Caffeine 不接受 null，且本项目刻意**不在 L1 缓存「不存在」**
     * （ADR 0004）。不存在的图片不会进入本缓存。
     */
    @Bean("pictureLocalCache")
    public Cache<Long, Picture> pictureLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(LOCAL_MAX_SIZE)
                .expireAfterWrite(LOCAL_EXPIRE_MINUTES, TimeUnit.MINUTES)
                // 开启命中率统计，便于观察缓存效果
                .recordStats()
                .build();
    }

    /**
     * 作者本地缓存：用户 id -> 作者视图
     * <p>
     * 与图片缓存刻意分离，使两者的失效互不牵连（ADR 0001）。
     */
    @Bean("userLocalCache")
    public Cache<Long, UserVO> userLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(LOCAL_MAX_SIZE)
                .expireAfterWrite(LOCAL_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}
