package com.example.demo.infra.adapter;

import com.example.demo.application.port.GatewayRateLimiterPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * <h2>[基礎設施層] Redis 分布式限流器適配器 (Sliding Window Strategy)</h2>
 * <p>
 * 實作 {@link GatewayRateLimiterPort} 介面。利用 Redis 的 Sorted Set (ZSET) 結構搭配 Lua 腳本，
 * 實作出毫秒級精度的分布式滑動窗口限流算法，確保網關節點橫向擴展時的互斥限流防線。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiterAdapter implements GatewayRateLimiterPort {

    private final StringRedisTemplate stringRedisTemplate;

    // 🚀 核心原子防線：宣告並載入 Lua 腳本
    private static final RedisScript<List> RATE_LIMIT_LUA_SCRIPT = new DefaultRedisScript<>(
            """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local max_requests = tonumber(ARGV[3])
            local clear_before = now - window_ms
            
            -- 1. 清除時間窗口以外的歷史過期請求紀錄
            redis.call('ZREMRANGEBYSCORE', key, 0, clear_before)
            
            -- 2. 獲取當前時間窗口內的總請求數
            local current_requests = redis.call('ZCARD', key)
            
            -- 3. 判斷是否超過限流閾值
            if current_requests < max_requests then
                -- 4. 未超額：將當前請求（以時間戳記充當分值與成員）塞入 ZSET
                redis.call('ZADD', key, now, now)
                -- 5. 動態更新 Key 的生命週期，防止冷數據無限期霸佔記憶體 (窗口時間轉秒後加緩衝)
                redis.call('EXPIRE', key, math.ceil(window_ms / 1000) + 2)
                
                -- 回傳 放行(1) 與 剩餘額度
                return {1, max_requests - current_requests - 1}
            else
                -- 5. 已超額：拒絕放行
                return {0, 0}
            end
            """,
            List.class
    );

    @Override
    public RateLimitResult isAllowed(String key, int maxRequests, int windowSeconds) {
        try {
            // 1. 組裝 Redis 專屬的限流專用 Key 邊界
            String redisKey = "gateway:rate_limit:" + key;

            // 2. 準備高精度時間參數（全域對齊毫秒，防止分散式節點微幅時鐘偏斜）
            long nowMs = Instant.now().toEpochMilli();
            long windowMs = (long) windowSeconds * 1000;

            // 3. 執行 Lua 腳本（傳入單一 Key 與三個參數）
            List<Long> result = stringRedisTemplate.execute(
                    RATE_LIMIT_LUA_SCRIPT,
                    Collections.singletonList(redisKey),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(maxRequests)
            );

            if (result == null || result.isEmpty()) {
                log.error("[RateLimiter] Redis 限流腳本回傳空值，執行安全降級放行。Key: {}", key);
                return new RateLimitResult(true, maxRequests);
            }

            boolean isAllowed = result.get(0) == 1L;
            long remainingTokens = result.get(1);

            log.info("[RateLimiter] 限流檢查完成 - Key: {}, 放行: {}, 剩餘額度: {}", key, isAllowed, remainingTokens);
            return new RateLimitResult(isAllowed, remainingTokens);

        } catch (Exception e) {
            // 🛡️ 護城河降級策略：當 Redis 發生網路抖動、連線中斷或滿載崩潰時，
            // 為了不阻斷核心業務流量，網關必須執行「安全降級（Fail-Open）」，直接放行。
            log.error("[RateLimiter] Redis 分布式限流器遭遇系統異常，啟動安全防禦降級放行！Key: {}", key, e);
            return new RateLimitResult(true, maxRequests);
        }
    }
}