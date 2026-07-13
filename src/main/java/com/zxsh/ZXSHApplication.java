package com.zxsh;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.zxsh.mapper")
@SpringBootApplication
public class ZXSHApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZXSHApplication.class, args);
    }

}
