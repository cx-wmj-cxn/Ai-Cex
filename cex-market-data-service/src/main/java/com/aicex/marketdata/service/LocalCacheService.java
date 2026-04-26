package com.aicex.marketdata.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class LocalCacheService {

    // 本地热点缓存，优先服务高频读请求，降低网络往返。
    private final Cache<String, Object> cache = Caffeine.newBuilder()
            .maximumSize(200_000)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();

    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = cache.getIfPresent(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }
}
