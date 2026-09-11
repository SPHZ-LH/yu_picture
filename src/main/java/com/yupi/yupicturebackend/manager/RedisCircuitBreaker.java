package com.yupi.yupicturebackend.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 访问熔断器。
 * <p>
 * <b>为什么需要它。</b>多级缓存的降级逻辑本身是对的（Redis 挂了就继续查库、接口不报错），
 * 但代价是**每一次 Redis 操作都要等满连接超时**。实测：Redis 不可达时，
 * 一个冷请求最多触发 4 次 Redis 操作（图片读+写、作者读+写），
 * 单请求耗时从 35ms 恶化到 15 秒；Tomcat 线程池会被迅速耗尽，
 * 于是「缓存故障」升级成「应用级雪崩」。
 * <p>
 * 本类的作用：连续失败到阈值后进入**熔断**状态，在冷却期内**直接拒绝访问 Redis**
 * （不再建立连接、不再等待超时），请求立刻走数据库。
 * 冷却结束后进入**半开**状态，只放行**一个**探测请求：成功则恢复，失败则重新熔断。
 * <p>
 * 全部状态用原子类型维护，无锁；日志只在**状态切换**时输出，
 * 避免故障期间刷屏（这是熔断带来的额外收益）。
 *
 * @author SPHZ
 */
@Slf4j
@Component
public class RedisCircuitBreaker {

    /**
     * 连续失败多少次后熔断。
     * 取 3 是权衡：真实故障时能在第一个请求内就触发保护；
     * 偶发的单次抖动不足以误熔断。
     */
    private static final int FAILURE_THRESHOLD = 3;

    /**
     * 熔断后的冷却时长。冷却期内完全不碰 Redis。
     */
    private static final Duration COOLDOWN = Duration.ofSeconds(10);

    private static final long COOLDOWN_NANOS = COOLDOWN.toNanos();

    /**
     * 连续失败次数
     */
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /**
     * 熔断到期时间（{@link System#nanoTime()} 基准）。0 表示当前未熔断。
     */
    private final AtomicLong openUntil = new AtomicLong(0L);

    /**
     * 半开状态下是否已放行过探测请求
     */
    private final AtomicLong probePermit = new AtomicLong(0L);

    /**
     * 是否允许本次访问 Redis。
     * <p>
     * 返回 false 时调用方应当**立即**按「缓存未命中/写入失败」处理，不要再尝试访问 Redis。
     *
     * @return true 表示可以访问
     */
    public boolean allowRequest() {
        long until = openUntil.get();
        if (until == 0L) {
            // 未熔断，正常放行
            return true;
        }
        if (System.nanoTime() < until) {
            // 熔断冷却中：快速失败，不访问 Redis，不等待超时
            return false;
        }
        // 冷却结束：半开，只放行一个探测请求，其余继续拒绝
        return probePermit.compareAndSet(0L, 1L);
    }

    /**
     * 记录一次成功。会立即恢复放行。
     */
    public void recordSuccess() {
        probePermit.set(0L);
        consecutiveFailures.set(0);
        if (openUntil.getAndSet(0L) != 0L) {
            log.warn("[熔断] Redis 已恢复，恢复正常访问");
        }
    }

    /**
     * 记录一次失败。达到阈值则进入熔断。
     */
    public void recordFailure() {
        probePermit.set(0L);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures < FAILURE_THRESHOLD) {
            return;
        }
        long until = System.nanoTime() + COOLDOWN_NANOS;
        // 仅在「从未熔断」变为「熔断」的那一刻打日志，避免刷屏
        if (openUntil.getAndSet(until) == 0L) {
            log.warn("[熔断] Redis 连续失败 {} 次，进入熔断：{} 秒内不再访问 Redis，请求直接走数据库",
                    failures, COOLDOWN.getSeconds());
        }
    }

    /**
     * 当前是否处于熔断（拒绝访问）状态，供监控使用。
     */
    public boolean isOpen() {
        long until = openUntil.get();
        return until != 0L && System.nanoTime() < until;
    }

    /**
     * 当前连续失败次数，供监控使用。
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
