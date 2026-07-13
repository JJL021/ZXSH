-- 1.参数列表
--1.1 优惠劵id （由于拼接起来才能形成完整的key，因此只能使用ARGV接收
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]
-- 1.3 新增 订单id，使用stream队列的消费者组直接向队列发消息
local orderId = ARGV[3]

-- 2.数据key
-- 2.1 库存key  String类型的key
local stockKey = "seckill:stock:" .. voucherId
--2.2 订单key  Set类型的key
local orderKey = 'seckill:order:' .. voucherId

-- 3. 脚本业务
--3.1 判断库存是否充足
if (tonumber(redis.call('get',stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end
--3.2 判断用户是否下过该优惠劵的单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 存在，说明是重复下单，返回2
    return 2
end

-- 3.3 扣减库存
redis.call('INCRBY',stockKey,-1)
-- 3.4 保存用户id到集合中
redis.call('SADD', orderKey, userId)
-- 3.5 发送消息到消息队列中，XADD stream.orders * k1 v1 k2 v2 ...
redis.call('XADD','stream.orders','*',"userId",userId,'voucherId',voucherId,'id',orderId)
return 0
