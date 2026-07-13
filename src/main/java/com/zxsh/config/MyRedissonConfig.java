package com.zxsh.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyRedissonConfig {

    @Bean
    public RedissonClient redissonClient1(){
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://10.39.255.75:6379").setPassword("123321");
        //创建redisson对象
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient2(){
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://10.39.255.75:6378");
        //创建redisson对象
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient3(){
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://10.39.255.75:6377");
        //创建redisson对象
        return Redisson.create(config);
    }
}
