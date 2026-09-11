package com.yupi.yupicturebackend.model.dto.cache;

import lombok.Data;

import java.io.Serializable;

/**
 * 手动失效缓存的执行结果。
 * <p>
 * <b>为什么接口总是返回成功，却要返回这些明细。</b>
 * 删除天然是幂等的——「目标 key 不存在」也算达成目标，不该报错打断运维操作。
 * 但管理员如果把 id 写错，一句「成功」会给虚假信心。所以返回各层的实际情况，
 * 让调用方能够区分「真清掉了」和「本来就没缓存」。
 *
 * @author SPHZ
 */
@Data
public class CacheEvictResult implements Serializable {

    /**
     * 目标缓存类型
     */
    private String type;

    /**
     * 目标 id
     */
    private Long id;

    /**
     * 一级缓存（本地 Caffeine）中是否存在该 key 并已清除
     */
    private boolean localRemoved;

    /**
     * 二级缓存（Redis）中是否存在该 key 并已清除
     */
    private boolean redisRemoved;

    /**
     * 二级缓存是否**因熔断被跳过**（完全没有尝试访问 Redis）。
     * <p>
     * 这个字段不能省：Redis 故障熔断期间 {@code redisRemoved} 一定是 false，
     * 但它**不代表** L2 里没有这条数据。若不区分，调用方会把
     * 「因为 Redis 挂了所以没清」误读成「L2 本来就没缓存」。
     * <p>
     * 出现 true 意味着：这次失效没有解决 L2 的脏数据，需要在 Redis 恢复后重新执行。
     */
    private boolean redisSkipped;

    private static final long serialVersionUID = 1L;
}
