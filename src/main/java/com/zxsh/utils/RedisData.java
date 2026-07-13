package com.zxsh.utils;

import lombok.Data;

import java.time.LocalDateTime;

//组合优于继承
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
