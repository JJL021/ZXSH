package com.zxsh.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.github.benmanes.caffeine.cache.Cache;
import com.zxsh.dto.Result;
import com.zxsh.entity.Shop;
import com.zxsh.mapper.ShopMapper;
import com.zxsh.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zxsh.utils.CacheClient;
import com.zxsh.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.zxsh.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //注入自己写的缓存工具类
    @Resource
    private CacheClient cacheClient;
    //本地缓存
    @Resource
    private Cache<Long, Shop> shopCache;
    //是否启用本地缓存
    private static final boolean USELOCALCACHE = true;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,id2 -> getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //1.先查本地缓存
        if(USELOCALCACHE){
            Shop shop= shopCache.getIfPresent(id);
            if(shop!= null){
                return Result.ok(shop);
            }
        }
        //2.未查到，查redis缓存
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
        //逻辑过期解决缓存击穿问题
        //Shop shop = queryWithLogicExpire(id);
        //Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        // 6.1写入本地缓存
        if (USELOCALCACHE) {
            shopCache.put((Long) id, shop);
            //log.debug("写入本地缓存"+id+":"+shop);

        }
        //返回
        return Result.ok(shop);
    }

    //缓存击穿的解决方案
/*    private Shop queryWithMutex(Long id){
        //1.从redis中查询商商铺信息
        String key = CACHE_SHOP_KEY+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.命中，直接返回
        if(StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断是否是空值(3种可能，存在不为空，存在但为""，不存在为null)
        if("".equals(shopJson)){
            //返回一个错误信息
            return null;
        }
        //3。实现缓存重建
        //3.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY+ id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //3.2判断是否获取成功
            if(!isLock){
                //3.3失败，则休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);  //重试就相当于递归 递归方法前记得加上return，不然方法递归结束程序还会继续往后执行，多次查询数据库
            }
            //注意L获取锁成功后应再次检查redis缓存是否存在，做doubleCheck,如果存在则无需重建缓存
            shopJson = stringRedisTemplate.opsForValue().get(key);
            //命中，直接返回
            if(StrUtil.isNotBlank(shopJson)){
                return JSONUtil.toBean(shopJson, Shop.class);
            }

            //3.4成功，根据id查询数据库
            shop = getById(id);
            //模拟重建延迟，延迟越高，重建时期并发的线程越多，出现并发安全问题，检验我们锁是否可靠
            Thread.sleep(100);
            //4.数据库中不存在，直接返回404
            if(shop == null){
                //将空值返回redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //5.数据库中存在，写入redis，重建缓存
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //6.释放互斥锁
            unLock(lockKey);
        }

        //7.返回商铺信息
        return shop;
    }*/


    //缓存穿透的解决方案：缓存空对象
/*    private Shop queryWithPassThrough(Long id){
        //1.从redis中查询商商铺信息
        String key = CACHE_SHOP_KEY+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.命中，直接返回
        if(StrUtil.isNotBlank(shopJson)){
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断是否是空值(3种可能，存在不为空，存在但为""，不存在为null)
        if("".equals(shopJson)){
            //返回一个错误信息
            return null;
        }
        //3.未命中，查询数据库
        Shop shop = getById(id);
        //4.数据库中不存在，直接返回404
        if(shop == null){
            //将空值返回redis
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //5.数据库中存在，写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //6.返回商铺信息
        return shop;
    }*/
    //缓存穿透的解决方案：逻辑过期
    /*private Shop queryWithLogicExpire(Long id){
        //1.从redis中查询商商铺信息
        String key = CACHE_SHOP_KEY+id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.未命中，直接返回null
        if(StrUtil.isBlank(shopJson)){
            return null;
        }
        //3.命中，把json反序列化未对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();//注意返回的object要强转，不然toBean没法调用
        Shop shop = JSONUtil.toBean(data,Shop.class);
        //4.判断是否过期
        if(redisData.getExpireTime().isAfter(LocalDateTime.now())){
            //4.1.未过期，直接返回店铺信息
            return shop;
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
                log.warn("逻辑过期的key已被清楚，无法测试");
                return null;
            }
            RedisData redisDataTemp = JSONUtil.toBean(redisDataJson, RedisData.class);
            //5.4.1 判断是否重建完成
            if(redisDataTemp.getExpireTime().isAfter(LocalDateTime.now())){
                //5.4.2重建完成，返回最新数据
                unLock(lockKey);
                return JSONUtil.toBean((JSONObject)redisDataTemp.getData(),Shop.class);
            }
            //5.5重建仍未完成
            //5.5.1开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(
                    ()->{
                        try {
                            //重建
                            saveShop2Redis(id,20L);
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
        return shop;
    }*/
    //缓存击穿的解决方案-逻辑过期方案中使用的工具函数
/*    public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(100);//模拟缓存重建耗时较长的特点
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入redis
        String key = CACHE_SHOP_KEY+id;
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }*/

    //加锁  缓存击穿问题解决方案 互斥锁
/*    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //防止拆箱出现空指针,属于hutool工具类
        return BooleanUtil.isTrue(flag);
    }*/
    //释放锁
/*    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }*/

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("id不能为空！");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除redis缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        //3.删除本地缓存
        shopCache.invalidate(id);
        return Result.ok();

    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        log.info("typeId={}, current={}, x={}, y={}", typeId, current, x, y);
        //1. 判断是否需要根据坐标查询
        if(x == null || y == null){
            //不需要坐标查询，按数据库查询
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            //返回数据
            return Result.ok(page.getRecords());
        }
        //2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        //3. SpringDataRedis GEOSEARCH — GeoSearchCommandArgs 替代 GeoRadiusCommandArgs（3.x 中后者对 GEOSEARCH 兼容性有 bug）
        String key = SHOP_GEO_KEY + typeId;
        log.info("查询的key={}", key);
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5, Metrics.KILOMETERS),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance()
                );
        //4.解析出id和距离，Java 侧做分页截取
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).limit(SystemConstants.DEFAULT_PAGE_SIZE).forEach(
                result -> {
                    String shopIdStr = result.getContent().getName();
                    ids.add(Long.valueOf(shopIdStr));
                    Distance distance = result.getDistance();
                    distanceMap.put(shopIdStr, distance);
                }
        );
        //5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6.返回
        return Result.ok(shops);
    }
}
