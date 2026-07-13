package com.zxsh;

import com.zxsh.entity.Shop;
import com.zxsh.service.impl.ShopServiceImpl;
import com.zxsh.utils.CacheClient;
import com.zxsh.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zxsh.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl service;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    private ExecutorService es = Executors.newFixedThreadPool(300);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopServiceImpl shopServiceImpl;

    @Test
    void testShopService() throws InterruptedException {
        Shop shop = service.getById(1L);
        cacheClient.setWithLogic(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
    }
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300); //多线程执行进度计数器
        //每个线程都创建500个id
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextID("order");
                System.out.println("id:"+id);
            }
            latch.countDown();
        };
        //线程开始之前计数
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        //线程全部完成后计数
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("end-begin:"+(end-begin));
    }
    @Test
    void testAddShopToRedis(){
        //1.查询店铺信息
        List<Shop> list = service.list();
        //2.把店铺按照typeId分组，typeId一致的放到一个集合 不使用for遍历而是使用强大的strean
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));//shop -> shop.getTypeid()
        //3.分批写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1 获取类型id
            Long typeId = entry.getKey();
            String key = "shop:geo:" + typeId;
            //3.2获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            ArrayList<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //3.3写入redis GEOADD key 经度 维度 member
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }

    }
}
