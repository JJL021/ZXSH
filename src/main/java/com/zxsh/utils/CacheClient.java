package com.zxsh.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;


import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.zxsh.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    //创建线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //将任意对象存入缓存并设置过期时间
    public void set(String key, Object object, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object),time,unit);
    }
    //将任意对象存入缓存并设置逻辑过期时间
    public void setWithLogic(String key,Object object,Long time,TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }
    //根据id查询缓存数据(防止缓存穿透版)
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,
            TimeUnit unit
    ){
        String key = keyPrefix+id;
        //1.从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            //3.存在，直接返回
            return JSONUtil.toBean(json,type);
        }
        //判断命中的是否是空值
        if(json != null){
            //返回一个错误信息
            return null;
        }
        //4.不存在，根据id查询数据库
        //本来是getById方法，但这里不知道是查哪个表，就无法使用getByID，因此这段逻辑只能用户传入进来
        R r = dbFallback.apply(id);
        //5. 不存在，返回错误
        if(r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6.存在，写入redis
        this.set(key,r,time,unit);
        //7.返回查询对象
        return r;
    }

    //缓存穿透的解决方案：逻辑过期
    public <R,ID> R queryWithLogicExpire(String keyPrefix,ID id, Class<R> type,Function<ID,R> dbFunction,
                                            Long time,TimeUnit unit){
        //1.从redis中查询商商铺信息
        String key = keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.未命中，直接返回null
        if(StrUtil.isBlank(json)){
            return null;
        }
        //3.命中，把json反序列化未对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();//注意返回的object要强转，不然toBean没法调用
        R r = JSONUtil.toBean(data,type);
        //4.判断是否过期
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //4.1.未过期，直接返回店铺信息
            return r;
        }
        //4.2.过期，需要重建缓存
        //5.缓存重建
        //5.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY+ id;
        boolean isLock = tryLock(lockKey);
        //5.2判断是否获取成功
        if(isLock){
            //5.3获取成功
            //5.4.DoubleCheck
            String redisDataJson = stringRedisTemplate.opsForValue().get(key);
            if(StrUtil.isBlank(redisDataJson)){
                log.warn("逻辑过期的key已被清除，无法测试");
                return null;
            }
            RedisData redisDataTemp = JSONUtil.toBean(redisDataJson, RedisData.class);
            //5.4.1 判断是否重建完成
            if(redisDataTemp.getExpireTime().isAfter(LocalDateTime.now())){
                //5.4.2重建完成，返回最新数据
                unLock(lockKey);
                return JSONUtil.toBean((JSONObject)redisDataTemp.getData(),type);
            }
            //5.5重建仍未完成
            //5.5.1开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(
                    ()->{
                        try {
                            //1.查询数据库
                            R r1 = dbFunction.apply(id);
                            //2.写入redis
                            this.setWithLogic(key,r1,time,unit);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            //释放锁
                            unLock(lockKey);
                        }

                    }
            );
        }

        //5.6返回过期的商铺信息
        return r;
    }

    //加锁  缓存击穿问题解决方案 互斥锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //防止拆箱出现空指针,属于hutool工具类
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}
