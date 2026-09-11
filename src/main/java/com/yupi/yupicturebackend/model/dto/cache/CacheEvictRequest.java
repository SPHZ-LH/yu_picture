package com.yupi.yupicturebackend.model.dto.cache;

import lombok.Data;

import java.io.Serializable;

/**
 * 手动失效缓存的请求。
 * <p>
 * 粒度刻意只做到**单个 key**：精确删除是 O(1) 的，清空整区则需要用 SCAN 遍历
 * （不能用会阻塞 Redis 的 KEYS），且有回源冲击，风险与收益不成比例。
 *
 * @author SPHZ
 */
@Data
public class CacheEvictRequest implements Serializable {

    /**
     * 缓存类型：picture / user
     */
    private String type;

    /**
     * 目标 id
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
