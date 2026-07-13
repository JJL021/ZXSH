package com.zxsh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.zxsh.dto.Result;
import com.zxsh.entity.VoucherOrder;
import com.zxsh.mapper.VoucherOrderMapper;
import com.zxsh.service.ISeckillVoucherService;
import com.zxsh.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zxsh.utils.RedisIdWorker;
import com.zxsh.utils.UserHolder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource(name = "redissonClient1")
    private RedissonClient redissonClient1;
    //创建luo脚本对象并初始化
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //声明serviceImpl类的代理对象
    private IVoucherOrderService proxy;

    //创建执行异步任务的线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 新增：容器销毁时关闭线程池
    @PreDestroy
    public void shutdown() {
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    private class VoucherOrderHandler implements Runnable {
        String queueName = "stream.orders";
        @Override
        public void run() {
            // 创建消费者组（Lettuce 6.6+ 要求组预先存在，旧版可自动创建）
            try {
                stringRedisTemplate.opsForStream().createGroup(queueName, "g1");
            } catch (Exception e) {
                // 组已存在，忽略
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    //1. 获取消息队列中的详细信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    //ps:这个消费者名字将来要配置到yaml文件，不同的节点应该有不同名字。这里先写死。
                    //ps:这里返回值是List是因为COUNT不一定是1
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //2. 判断获取消息是否成功
                    if(list == null || list.isEmpty()){
                        //2.1如果获取失败，说明没有消息，继续下一次循环
                        continue;
                    }
                    //3.解析消息中的订单信息
                    //ps:这里三个泛型分别代表消息id，存储的键、值对类型
                    MapRecord<String, Object, Object> record = list.get(0);
                    //3.1读取键值对信息，即我们存入的'id':orderId,'voucherId':voucherId等
                    Map<Object, Object> values = record.getValue();
                    //3.2转换为VoucherOrder对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.2 获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //3. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    // 线程被中断（容器关闭）时，直接退出循环，不再重试
                    if (Thread.currentThread().isInterrupted()) {
                        log.info("VoucherOrderHandler 收到中断信号，正常退出");
                        break;
                    }
                    log.error("处理订单异常", e);
                    handlerPendingList();
                }
            }
            log.info("VoucherOrderHandler 线程正常退出");
        }

        private void handlerPendingList() {
            while (true) {
                try {
                    //1. 获取pending list的订单信息
                    // ps: XREADGROUP GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2. 判断获取消息是否成功
                    if(list == null || list.isEmpty()){
                        //2.1如果获取失败，说明没有消息，直接结束循环
                        break;
                    }
                    //3.解析消息中的订单信息
                    //ps:这里三个泛型分别代表消息id，存储的键、值对类型
                    MapRecord<String, Object, Object> record = list.get(0);
                    //3.1读取键值对信息，即我们存入的'id':orderId,'voucherId':voucherId等
                    Map<Object, Object> values = record.getValue();
                    //3.2转换为VoucherOrder对象
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //2.2 获取成功，可以下单
                    handleVoucherOrder(voucherOrder);
                    //3. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());

                } catch (Exception e) {
                    //打印异常信息后进入下一次while
                    log.error("处理pending list订单异常", e);
                    try {
                        //防止执行频率过高
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }
/*    //创建java的阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);
    //    创建一个内部类 让service类一初始化就执行run  @PostConstruct注解代表在当前类初始化完毕后执行
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //1. 获取阻塞队列中的详细信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    //2. 创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //调用写数据库的方法（事务）
        proxy.createVoucherOrderSimple(voucherOrder);

    }

    @Transactional
    public void createVoucherOrderSimple(VoucherOrder voucherOrder){
        //扣减库存
        boolean isSuccess = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if(!isSuccess){
            log.error("库存不足！（必定不会执行）");
        }
        //保存订单
        save(voucherOrder);
    }
    //改用redis的消息队列实现异步秒杀
    public Result secKillVoucher(Long voucherId) throws InterruptedException {
        //1. 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy(); //事务必须通过代理对象调用
        //2. 执行lua脚本
        //2.1获取用户id
        Long userId = UserHolder.getUser().getId();
        // 2.2 获取订单id
        long orderId = redisIdWorker.nextID("order");
        //2.3执行lua脚本
        Long executeResult = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(), //不能传null
                voucherId.toString(), userId.toString(),String.valueOf(orderId));
        //3.判断结果是否为0
        int r = executeResult.intValue();
        if(r != 0){
            //2.1不为0，，返回错误信息
            return Result.fail(r == 1 ? "优惠劵已被抢光":"不能重复下单");
        }

        //4.返回订单id
        return Result.ok(orderId);
    }
/*    //使用java的阻塞队列实现异步秒杀
    public Result secKillVoucher(Long voucherId) throws InterruptedException {
        //1. 执行lua脚本
        Long userId = UserHolder.getUser().getId();
        Long executeResult = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(), //不能传null
                voucherId.toString(), userId.toString());
        //2.判断结果是否为0
        int r = executeResult.intValue();
        if(r != 0){
            //2.1不为0，，返回错误信息
            return Result.fail(r == 1 ? "优惠劵已被抢光":"不能重复下单");
        }
        //2.2 为0，有购买资格，把下单信息保存到阻塞队列中
        VoucherOrder voucherOrder = new VoucherOrder();
        // 2.3 订单id
        long orderId = redisIdWorker.nextID("order");
        voucherOrder.setId(orderId);
        // 2.4 优惠劵id
        voucherOrder.setVoucherId(voucherId);
        // 2.5 用户id
        voucherOrder.setUserId(userId);
        // 2.6 放入阻塞队列
        orderTasks.add(voucherOrder);
        //3. 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy(); //事务必须通过代理对象调用
        //4.返回订单id
        return Result.ok(orderId);
    }*/
/*    @Override
    public Result secKillVoucher(Long voucherId) throws InterruptedException {
        //1.查询优惠劵信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断是否开始
        //2.1 未开始，返回异常结果
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("该优惠卷秒杀活动未开始！");
        }
        //2.2 判断秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("该优惠卷秒杀活动已结束！");
        }
        //2.2开始，判断库存
        if(voucher.getStock()<1)
        {        //3.库存不足，返回异常结果
            return Result.fail("优惠劵库存不足，抢购失败");
        }
        //3.库存充足，创建订单
        //一人一单
        Long userId = UserHolder.getUser().getId();
        //获取锁对象
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:"+userId,stringRedisTemplate);
        RLock lock = redissonClient1.getLock("lock:order:" + userId);
        boolean isSuccess = lock.tryLock(1,TimeUnit.SECONDS);
        if(!isSuccess){
            return Result.fail("不允许重复下单！");
        }
        try {
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy(); //事务必须通过代理对象调用
            return proxy.createVoucherOrder(voucherId);
        } catch (IllegalStateException e) {
            throw new RuntimeException(e);
        } finally {
            //释放锁
            lock.unlock();
        }
    }*/

    //加悲观锁，乐观锁是更改数据库用的，这里是查询数据库
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        long count = query().eq("user_id",  userId).eq("voucher_id", voucherId).count();
        if(count>0){
            return Result.fail("该用户已经购买过了！");
        }
        //3.1库存充足，扣减库存
        boolean isSuccess = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).gt("stock",0) //加上最后的stock对比形成乐观锁
                .update();
        if(!isSuccess){
            return Result.fail("优惠劵库存不足，抢购失败");
        }
        //4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextID("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        //5.返回订单id
        return Result.ok(orderId);


    }
}
