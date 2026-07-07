package com.example.demo.config;

import com.example.demo.infra.ratelimit.RateLimitFilterFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * <h2>[網關層] 全局路由矩陣宣告配置類別 (Type-Safe 絕對防禦版)</h2>
 */
@Configuration
public class GatewayRoutesConfiguration {

    private final RateLimitFilterFactory rateLimitFactory;

    public GatewayRoutesConfiguration(RateLimitFilterFactory rateLimitFactory) {
        this.rateLimitFactory = rateLimitFactory;
    }

    @Bean
    public RouterFunction<ServerResponse> saasGatewayRoutes() {

        // ===================================================================
        // 構建路由矩陣
        // ===================================================================
        RouterFunction<ServerResponse> routes = route("auth-public-route")
                .route(path("/api/auth/**"), http())
                .filter(rateLimitFactory.ipRateLimiter("auth-public", 3, 10))
                .filter(circuitBreaker("authCircuitBreaker", URI.create("forward:/fallback/auth")))
                .before(uri("http://localhost:8080"))
                .build()

                .and(route("auth-admin-route")
                        .route(path("/api/users/**")
                                .or(path("/api/roles/**"))
                                .or(path("/api/groups/**"))
                                .or(path("/api/permissions/**")), http())
                        .filter(rateLimitFactory.ipRateLimiter("auth-admin", 10, 10))
                        .filter(circuitBreaker("authCircuitBreaker", URI.create("forward:/fallback/auth")))
                        .before(uri("http://localhost:8080"))
                        .build())

                .and(route("department-service-route")
                        .route(path("/api/departments/**"), http())
                        .filter(rateLimitFactory.ipRateLimiter("dept-api", 10, 10))
                        .filter(circuitBreaker("deptCircuitBreaker", URI.create("forward:/fallback/department")))
                        .before(uri("http://localhost:8081"))
                        .build())

                .and(route("kyc-service-route")
                        .route(path("/api/kyc/**"), http())
                        .filter(rateLimitFactory.ipRateLimiter("kyc-api", 10, 10))
                        .filter(circuitBreaker("kycCircuitBreaker", URI.create("forward:/fallback/kyc")))
                        .before(uri("http://localhost:8083"))
                        .build())

                .and(route("tenant-service-route")
                        .route(path("/api/v1/platform/tenants")
                                .or(path("/api/v1/platform/tenants/**"))
                                .or(path("/api/platform/tenants"))
                                .or(path("/api/platform/tenants/**")), http())
                        .filter(rateLimitFactory.ipRateLimiter("tenant-admin", 5, 10))
                        .filter(circuitBreaker("tenantCircuitBreaker", URI.create("forward:/fallback/tenant")))
                        .before(uri("http://localhost:8082"))
                        .build());

        // ===================================================================
        // 全局兜底防護網與 Header 注入 (Global Filters)
        // ===================================================================
        return routes
                // 1. 核心修復：攔截 Servlet Attribute 並注入為原生的 HTTP Header，確保下游收得到
                .filter((request, next) -> {
                    HttpServletRequest servletRequest = request.servletRequest();
                    String tenantId = (String) servletRequest.getAttribute("X-Tenant-Id");
                    String userId = (String) servletRequest.getAttribute("X-User-Id");
                    String permissions = (String) servletRequest.getAttribute("X-User-Permissions");

                    // 埋入網關出站前的 Log (建議在正式上線 Production 時可以調成 log.debug 甚至關閉)
                    System.out.println("=================================================");
                    System.out.println("[SCG 網關準備轉發] 目標路徑: " + request.uri());
                    System.out.println("  -> 注入 Header [X-Tenant-Id]: " + tenantId);
                    System.out.println("  -> 注入 Header [X-User-Permissions]: " + permissions);
                    System.out.println("=================================================");

                    // 若有 tenantId，代表此請求已通過 JWT 驗證
                    if (tenantId != null) {
                        // 終極資安修復：使用 .set() 強制覆寫，防堵 Header 疊加 (WPG,WPG) 與惡意竄改
                        ServerRequest mutatedRequest = ServerRequest.from(request)
                                .headers(httpHeaders -> {
                                    httpHeaders.set("X-Tenant-Id", tenantId);
                                    httpHeaders.set("X-User-Id", userId != null ? userId : "");
                                    httpHeaders.set("X-User-Permissions", permissions != null ? permissions : "");

                                    // 零信任架構：拔除原始 JWT Token，確保下游微服務只能依賴網關驗證過的資訊
                                    httpHeaders.remove("Authorization");
                                })
                                .build();
                        return next.handle(mutatedRequest);
                    }

                    // 若無 tenantId，代表是白名單路徑 (如 /api/auth/login)，直接放行
                    return next.handle(request);
                })
                // 2. 全域基礎限流
                .filter(rateLimitFactory.ipRateLimiter("global-baseline", 50, 1));
    }
}