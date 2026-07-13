package com.zxsh.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;


import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;  //不同业务使用不同的锁
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String  ID_PREFIX = UUID.randomUUID().toString(true) + "-"; //运行时调用方法计算出来的 用来标识不同的 JVM 进程
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //value加上线程的标识(UUI+threadID)
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isSuccess); //防止自动拆箱出现空指针风险
    }

    @Override
    public void unLock() {
        //调用lua脚本，实现查询与删除的原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX+Thread.currentThread().getId());
        //不关心返回值了
    }

    /*    @Override
    public void unLock() {
        String threadId = Thread.currentThread().getId()+ID_PREFIX;
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        //释放锁
        if(!threadId.equals(value)){return;}
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }*/
}
