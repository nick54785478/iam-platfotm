package com.example.demo.infra.adapter;

import com.example.demo.application.port.GatewayRateLimiterPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <h2>[網關層] 本地記憶體限流實作 (Local In-Memory)</h2>
 * <p>適用於單機部署或系統初期。透過 application.yml 控制是否啟用。</p>
 */
@Component
// 開關：當 app.ratelimit.type = local 時，或未設定時預設啟動此 Bean
@ConditionalOnProperty(name = "app.ratelimit.type", havingValue = "local", matchIfMissing = true)
class MemoryRateLimiterAdapter implements GatewayRateLimiterPort {

    // 儲存 Key 與其對應的計數器與過期時間
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public RateLimitResult isAllowed(String key, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();

        TokenBucket bucket = buckets.compute(key, (k, existingBucket) -> {
            // 如果不存在，或已經超過時間窗口，則重置計數器
            if (existingBucket == null || now > existingBucket.expiresAt) {
                return new TokenBucket(new AtomicInteger(1), now + (windowSeconds * 1000L));
            }
            // 仍在時間窗口內，累加請求數
            existingBucket.count.incrementAndGet();
            return existingBucket;
        });

        int currentCount = bucket.count.get();
        boolean allowed = currentCount <= maxRequests;
        long remaining = Math.max(0, maxRequests - currentCount);

        return new RateLimitResult(allowed, remaining);
    }

    private record TokenBucket(AtomicInteger count, long expiresAt) {
    }
}