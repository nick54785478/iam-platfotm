package com.example.demo.infra.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import security.port.RuleCacheManagerPort;

import java.time.Duration;
import java.util.Set;

/**
 * 實作 API Resource Rule 快取管理類
 *
 * <p>實作 Shared Kernel 的 RuleCacheManagerPort</p>
 */
@Component
@RequiredArgsConstructor
class RuleCacheManagerAdapter implements RuleCacheManagerPort {

    private final StringRedisTemplate redisTemplate;

    @Override
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public void deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
