package com.example.demo.infra.ratelimit;

import com.example.demo.application.port.GatewayRateLimiterPort;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;

/**
 * <h2>[網關層] 高規自訂限流過濾器函數工廠 (Thread-Local Servlet 阻斷版)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本元件專職為 Spring Cloud Gateway Server WebMVC 路由鏈提供強大的限流過濾器（Filter）。
 * 支援「全局兜底防護」與「高危端點精準狙擊」之雙層限流架構策略。
 * </p>
 * <p>
 * <b>【極限天坑排除 (UnsupportedOperationException)】</b>：<br>
 * 傳統 WebMVC Filter 若透過回傳自訂 {@link ServerResponse} 進行中斷，響應體在經過外層過濾器回傳傳導時，
 * 底層 Tomcat/Spring 標頭容器會被強行鎖定為不可變（Immutable）。此時若外層安全過濾器試圖修改 Header，將引發暴斃。<br>
 * 本實作採用硬核黑科技：當觸發限流時，利用 {@link RequestContextHolder} 拔出當前執行緒綁定的原生 {@link HttpServletResponse}，
 * 實施就地二進位串流寫入並強行 {@code flush()} 阻斷，完美繞過 WebMVC 回程鏈路標頭死鎖。
 * </p>
 *
 */
public class RateLimitFilterFunctions {

    /**
     * 構建基於客戶端 IP（Client IP）維度的邊界防禦限流過濾器。
     * <p>
     * 內部演算法全面對齊策略模式（Strategy Pattern），具體計數與扣減扣款邏輯交由抽象的 {@link GatewayRateLimiterPort} 執行，
     * 支援本地記憶體滑動窗口與 Redis 分布式 Lua 腳本限流之無縫可插拔抽換。
     * </p>
     *
     * @param rateLimiter   限流器策略實作 Bean (如 LocalMemoryRateLimiter 或未來擴充的 RedisRateLimiter)
     * @param routeId       路由代號標識，用於隔離不同業務端點的流量桶 (例如 "global-baseline", "auth-public")
     * @param maxRequests   在指定時間窗口內允許的最大請求上限次數
     * @param windowSeconds 時間窗口大小（單位：秒）
     * @return 符合 Spring Cloud Gateway MVC 規格的 {@link HandlerFilterFunction} 過濾器函數
     */
    public static HandlerFilterFunction<ServerResponse, ServerResponse> ipRateLimiter(
            GatewayRateLimiterPort rateLimiter, String routeId, int maxRequests, int windowSeconds) {

        return (request, next) -> {

            /*
             * ===================================================================
             * 🌐 1. 客戶端真實 IP 拔取 (Anti-Proxy Bypass)
             * ===================================================================
             * 優先從反向代理（如 Nginx、Cloudflare）標準的 X-Forwarded-For 標頭提取真實來源 IP。
             * 若缺失，則 fallback 回退至 TCP Socket 直連的遠端位址。
             */
            String ip = request.headers().firstHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = request.remoteAddress().map(addr -> addr.getAddress().getHostAddress()).orElse("UNKNOWN");
            }

            // 構建全域唯一的流量桶 Key (格式：RATE_LIMIT:{路由ID}:IP:{IP位址})
            String limitKey = String.format("RATE_LIMIT:%s:IP:%s", routeId, ip);

            /*
             * ===================================================================
             * ⚖️ 2. 流量盤查與扣款 (Rate Limit Verification)
             * ===================================================================
             * 呼叫策略介面判定是否允許放行。回傳結果內含原子扣減後的剩餘權杖數。
             */
            GatewayRateLimiterPort.RateLimitResult result = rateLimiter.isAllowed(limitKey, maxRequests, windowSeconds);

            if (result.isAllowed()) {
                /*
                 * ===================================================================
                 * 情況 A：配額充足，通航放行 (Pass Through)
                 * ===================================================================
                 * 允許請求進入下游微服務。在回程響應時，嘗試在 Header 附加標準的剩餘額度。
                 * 加上 try-catch 靜默防禦，防止極端情況下底層容器鎖定標頭引發系統異常。
                 */
                ServerResponse response = next.handle(request);
                try {
                    response.headers().add("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
                } catch (UnsupportedOperationException e) {
                    // 標頭已不可變時，靜默放棄附加，優先保證業務流量順暢穿透
                }
                return response;

            } else {
                /*
                 * ===================================================================
                 * 情況 B：流量超標，就地擊落 (Rate Limited - 429 Abort)
                 * ===================================================================
                 * 透過 Thread-Local 上下文安全拔取當前 Web 執行緒持有的原生 HttpServletResponse。
                 * 直接就地執行二進位 JSON 寫入並斷電，徹底終結請求生命週期，不給下游與外層 Filter 添亂。
                 */
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (attributes != null) {
                    HttpServletResponse servletResponse = attributes.getResponse();
                    if (servletResponse != null) {
                        send429TooManyRequests(servletResponse, windowSeconds);
                    }
                }

                // 回傳 429 狀態碼物件告知 WebMVC 路由矩陣：鏈路已在此斷開，無需後續轉發處理
                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
        };
    }

    /**
     * 動用底層硬核 Servlet 輸出流，就地強行灌入標準的 HTTP 429 JSON 錯誤結構。
     *
     * @param response      原生的 HttpServletResponse
     * @param windowSeconds 冷卻懲罰時間（秒），用於告知前端 Retry-After 的精準時機
     * @throws IOException 當 I/O 串流寫入失敗時拋出
     */
    private static void send429TooManyRequests(HttpServletResponse response, int windowSeconds) throws IOException {
        // 設定標準的 HTTP 429 狀態碼與 JSON 響應標頭
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        // 遵照 RFC 6585 規範：告訴客戶端到底要冷卻多少秒才能重新發起突襲
        response.setHeader("X-RateLimit-Retry-After", String.valueOf(windowSeconds));

        // 封裝高度標準化的 SaaS 業務限流降級 JSON Payload
        String jsonPayload = String.format(
                "{\"code\":\"IAM-429-RATE-LIMIT\",\"message\":\"您的請求過於頻繁，已觸發安全限流機制，請於 %d 秒後再試。\"}",
                windowSeconds
        );

        // 強行沖刷緩存區，關閉閘門
        response.getWriter().write(jsonPayload);
        response.getWriter().flush();
    }
}