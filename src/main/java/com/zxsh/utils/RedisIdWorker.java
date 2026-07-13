package com.zxsh.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

//基于redis的全局唯一ID生成器 满足唯一性、高可用、递增性、安全性、高性能
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1767225600L;
    private static final int MOVE_BITS = 32;

    private StringRedisTemplate stringRedisTemplate; //利用自增长

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextID(String keyPrefix){
        //1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //2.生成序列号
        //2.1获取当前日期，精确到天
        String day = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2自增长 即使是新key也不会空指针，会自动创建key，然后加0得到1
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + day);

        //3.拼接并返回
        return timestamp << MOVE_BITS |  count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second); //1767225600
    }

}
