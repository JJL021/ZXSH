package com.zxsh.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zxsh.entity.Shop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineConfig {

    //Shop本地缓存对象
    @Bean
    public Cache<Long, Shop> shopCache(){
        return Caffeine.newBuilder()
                .initialCapacity(15)
                .maximumSize(10_0)  //// 设置缓存大小上限为 100
                .expireAfterWrite(Duration.ofSeconds(60)) // 设置缓存有效期为 10 秒，从最后一次写入开始计时
                .recordStats() //记录缓存命中率等信息
                .build();
    }

}
