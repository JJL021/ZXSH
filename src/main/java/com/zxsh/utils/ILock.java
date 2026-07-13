package com.zxsh.utils;

public interface ILock {

    /**
     * 尝试获取锁，获取不到返回false，不阻塞等待释放
     * @param timeoutSec 锁自动释放的超时时间
     * @return true代表获取成功，false代表获取失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
