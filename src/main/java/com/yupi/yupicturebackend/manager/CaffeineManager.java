package com.yupi.yupicturebackend.manager;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @author SPHZ
 * @version 1.0
 * @createDate 2026-01-21
 * @description 本地缓存服务
 */
@Component
public class CaffeineManager {

    /**
     * 构造本地缓存 Caffeine 设置容量和过期时间
     */
    @Bean
    public Cache<String, String> localCache() {
        return Caffeine.newBuilder().initialCapacity(1024).maximumSize(10000L)
                // 缓存 5 分钟移除
                .expireAfterWrite(5L, TimeUnit.MINUTES).build();
    }

}
