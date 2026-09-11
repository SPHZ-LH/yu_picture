package com.yupi.yupicturebackend.controller;

import com.yupi.yupicturebackend.annotation.AuthCheck;
import com.yupi.yupicturebackend.common.BaseResponse;
import com.yupi.yupicturebackend.common.ResultUtils;
import com.yupi.yupicturebackend.constant.UserConstant;
import com.yupi.yupicturebackend.exception.ErrorCode;
import com.yupi.yupicturebackend.exception.ThrowUtils;
import com.yupi.yupicturebackend.manager.MultiLevelCacheManager;
import com.yupi.yupicturebackend.model.dto.cache.CacheEvictRequest;
import com.yupi.yupicturebackend.model.dto.cache.CacheEvictResult;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.enums.CacheTypeEnum;
import com.yupi.yupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 缓存运维接口。
 * <p>
 * 为什么需要手动失效（而不是完全依赖自动失效）：自动失效的钩子只覆盖
 * **经过应用的写入**。如果有人直接改数据库（运维脚本、其它系统、手工改库），
 * 应用根本不知道，缓存最长会脏到 L2 的 TTL（30 分钟），而且**重启应用也没用**
 * ——L1 清空后会被 L2 里的旧值重新填满。本接口就是这种场景下唯一精确的补救手段。
 * <p>
 * 也正因为它是「兜底手段」，它的使用情况本身就是信号：
 * 如果审计日志里同一个 key 反复出现，说明有写路径绕过了自动失效钩子。
 *
 * @author SPHZ
 */
@Slf4j
@RestController
@RequestMapping("/cache")
public class CacheController {

    @Resource
    private MultiLevelCacheManager multiLevelCacheManager;

    @Resource
    private UserService userService;

    /**
     * 手动失效指定缓存（仅管理员）。
     * <p>
     * 粒度刻意只做到单个 key：精确删除是 O(1)，而清空整区需要用 SCAN 遍历
     * （不能用会阻塞 Redis 的 KEYS），且有回源冲击。
     * <p>
     * <b>鉴权说明</b>：{@link AuthCheck} 依赖 Spring AOP，只有从外部经代理调用才生效。
     * 本方法必须直接调用 {@code multiLevelCacheManager}，**不能**通过本类内部的
     * 其它方法间接调用，否则会重蹈 UserController 里鉴权被自调用绕过的覆辙。
     *
     * @param cacheEvictRequest 失效请求（type + id）
     * @param request           用于取当前登录用户做审计
     * @return 各层实际清除情况
     */
    @PostMapping("/evict")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<CacheEvictResult> evictCache(@RequestBody CacheEvictRequest cacheEvictRequest,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(cacheEvictRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = cacheEvictRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR, "id 必须为正整数");
        CacheTypeEnum typeEnum = CacheTypeEnum.getEnumByValue(cacheEvictRequest.getType());
        ThrowUtils.throwIf(typeEnum == null, ErrorCode.PARAMS_ERROR, "type 只能是 picture 或 user");

        // 直接调用缓存管理器（不可经本类内部方法转发，否则 @AuthCheck 失效）
        CacheEvictResult result;
        if (CacheTypeEnum.PICTURE.equals(typeEnum)) {
            result = multiLevelCacheManager.evictPicture(id);
        } else {
            result = multiLevelCacheManager.evictUser(id);
        }

        // 审计日志。价值不止于追责——同一个 key 被反复手动清，就是写路径漏网的信号。
        User loginUser = userService.getLoginUser(request);
        log.info("[缓存手动失效] 操作人={}({}) type={} id={} localRemoved={} redisRemoved={} redisSkipped={}",
                loginUser.getUserName(), loginUser.getId(), typeEnum.getValue(), id,
                result.isLocalRemoved(), result.isRedisRemoved(), result.isRedisSkipped());

        return ResultUtils.success(result);
    }

    /**
     * 查看缓存运行状态（仅管理员）。
     * <p>
     * 返回两级 L1 的命中率统计，以及 L2（Redis）熔断器状态。
     * <p>
     * 重点关注 {@code l2Circuit.open}：
     * <ul>
     *   <li>{@code true} —— Redis 正在故障，缓存已完全退化为「直查数据库」，
     *       数据库压力会明显上升，同时手动失效的 {@code redisSkipped} 会变成 true。</li>
     *   <li>{@code consecutiveFailures} —— 连续失败次数，接近阈值时说明 Redis 不稳定。</li>
     * </ul>
     * 另外 {@code pictureLocal.hitRate} 偏低通常意味着图片详情访问分散，
     * 或缓存刚被大量失效过。
     *
     * @return 缓存状态快照
     */
    @GetMapping("/stats")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Map<String, Object>> getCacheStats() {
        return ResultUtils.success(multiLevelCacheManager.getCacheStats());
    }
}
