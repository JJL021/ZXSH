package com.zxsh.controller;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.zxsh.dto.Result;
import com.zxsh.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("sys-info")
public class SystemInfoController {
    @Resource
    private Cache<Long, Shop> shopCache;

    @GetMapping("/cache-stats")
    public Result queryTypeList() {
        log.info("cache stats: {}", shopCache.stats());
        CacheStats stats = shopCache.stats();
        Map<String, Object> result = new HashMap<>();
        result.put("hitCount", stats.hitCount());
        result.put("missCount", stats.missCount());
        result.put("hitRate", stats.hitRate());
        result.put("missRate", stats.missRate());
        result.put("loadSuccessCount", stats.loadSuccessCount());
        result.put("loadFailureCount", stats.loadFailureCount());
        result.put("totalLoadTime", stats.totalLoadTime());
        result.put("evictionCount", stats.evictionCount());
        result.put("evictionWeight", stats.evictionWeight());
        return Result.ok(result);
    }
}
