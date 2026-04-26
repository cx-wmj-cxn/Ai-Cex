package com.aicex.marketdata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class RedisCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        return deserialize(json, type);
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException ignored) {
            // 缓存失败不影响主流程，优先保证行情链路可用性。
            // ignore cache encode failures and continue to serve from in-memory state
        }
    }

    public void putPersistent(String key, Object value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json);
        } catch (JsonProcessingException ignored) {
            // ignore cache encode failures and continue to serve from in-memory state
        }
    }

    public <T> List<T> listByPrefix(String prefix, Class<T> type) {
        Set<String> keys = redisTemplate.keys(prefix + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> raw = redisTemplate.opsForValue().multiGet(keys);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<T> values = new ArrayList<>(raw.size());
        for (String json : raw) {
            if (json == null) {
                continue;
            }
            deserialize(json, type).ifPresent(values::add);
        }
        return values;
    }

    private <T> Optional<T> deserialize(String json, Class<T> type) {
        try {
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
