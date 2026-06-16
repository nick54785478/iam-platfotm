package com.example.demo.application.port;


/**
 * <h2>[網關層] 分布式限流器統一介面 (Strategy Pattern)</h2>
 * <p>
 * 定義網關層限流的標準合約。允許系統在「本地記憶體限流」與「Redis 分布式限流」之間無縫切換。
 * </p>
 */
public interface GatewayRateLimiterPort {

    /**
     * 檢查指定 Key 是否允許放行。
     *
     * @param key           限流維度標識 (例如: "IP:192.168.1.1", "USER:V-NICK", "ROUTE:auth-register")
     * @param maxRequests   時間窗口內允許的最大請求數
     * @param windowSeconds 時間窗口大小 (單位：秒)
     * @return 包含是否放行 (isAllowed) 與剩餘額度等資訊的結果物件
     */
    RateLimitResult isAllowed(String key, int maxRequests, int windowSeconds);

    /**
     * 限流結果 DTO
     */
    record RateLimitResult(boolean isAllowed, long remainingTokens) {
    }
}