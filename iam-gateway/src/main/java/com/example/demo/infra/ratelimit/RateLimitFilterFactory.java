package com.example.demo.infra.ratelimit;

import com.example.demo.application.port.GatewayRateLimiterPort;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;


import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;

/**
 * <h2>[網關層] 高規自訂限流過濾器工廠 (Spring Bean + Qualifier 精準注入版)</h2>
 * <p>
 * <b>【架構升級】</b>：<br>
 * 徹底拋棄傳統靜態方法 (Static Utility) 的反模式，升格為 Spring 容器託管的 @Component。<br>
 * 透過 @Qualifier 精準鎖定底層限流引擎，讓 WebMVC 路由配置檔完全與限流策略解耦。
 * </p>
 */
@Component
public class RateLimitFilterFactory {

    // 🚀 核心防線：透過 Qualifier 鎖定 Redis 分布式限流器 (Bean 名稱預設為類別首字母小寫)
    private final GatewayRateLimiterPort rateLimiter;

    public RateLimitFilterFactory(@Qualifier("redisRateLimiterAdapter") GatewayRateLimiterPort rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    /**
     * 構建基於客戶端 IP（Client IP）維度的邊界防禦限流過濾器。
     *
     * @param routeId       路由代號標識，用於隔離不同業務端點的流量桶 (例如 "global-baseline", "auth-public")
     * @param maxRequests   在指定時間窗口內允許的最大請求上限次數
     * @param windowSeconds 時間窗口大小（單位：秒）
     * @return 符合 Spring Cloud Gateway MVC 規格的 {@link HandlerFilterFunction} 過濾器函數
     */
    public HandlerFilterFunction<ServerResponse, ServerResponse> ipRateLimiter(
            String routeId, int maxRequests, int windowSeconds) {

        return (request, next) -> {

            /*
             * ===================================================================
             * 🌐 1. 客戶端真實 IP 拔取 (Anti-Proxy Bypass)
             * ===================================================================
             */
            String ip = request.headers().firstHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) {
                ip = request.remoteAddress().map(addr -> addr.getAddress().getHostAddress()).orElse("UNKNOWN");
            }

            // 構建全域唯一的流量桶 Key (格式：RATE_LIMIT:{路由ID}:IP:{IP位址})
            String limitKey = String.format("RATE_LIMIT:%s:IP:%s", routeId, ip);

            /*
             * ===================================================================
             * ⚖️ 2. 流量盤查與扣款 (利用 IoC 注入的 Redis 引擎)
             * ===================================================================
             */
            GatewayRateLimiterPort.RateLimitResult result = rateLimiter.isAllowed(limitKey, maxRequests, windowSeconds);

            if (result.isAllowed()) {
                /*
                 * ===================================================================
                 * 情況 A：配額充足，通航放行 (Pass Through)
                 * ===================================================================
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
                 */
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if (attributes != null) {
                    HttpServletResponse servletResponse = attributes.getResponse();
                    if (servletResponse != null) {
                        send429TooManyRequests(servletResponse, windowSeconds);
                    }
                }

                return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS).build();
            }
        };
    }

    /**
     * 動用底層硬核 Servlet 輸出流，就地強行灌入標準的 HTTP 429 JSON 錯誤結構。
     */
    private void send429TooManyRequests(HttpServletResponse response, int windowSeconds) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("X-RateLimit-Retry-After", String.valueOf(windowSeconds));

        String jsonPayload = String.format(
                "{\"code\":\"IAM-429-RATE-LIMIT\",\"message\":\"您的請求過於頻繁，已觸發安全限流機制，請於 %d 秒後再試。\"}",
                windowSeconds
        );

        response.getWriter().write(jsonPayload);
        response.getWriter().flush();
    }
}