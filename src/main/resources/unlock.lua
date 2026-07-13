-- 锁的key传递进来
local key = KEYS[1]

local value = redis.call('get',key)
-- 当前线程表示传递进来
local threadId = ARGV[1]
-- 比较key对应的值是不是当前线程表示
if(value == threadId) then
    -- 释放锁 del key
    return redis.call('del',key)
end
return 0
