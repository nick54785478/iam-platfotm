package com.example.demo.config;

import com.example.demo.application.port.GatewayRateLimiterPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static com.example.demo.infra.ratelimit.RateLimitFilterFunctions.ipRateLimiter;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * <h2>[網關層] 全局路由矩陣宣告配置類別 (Type-Safe 絕對防禦版)</h2>
 * <p>
 * 本配置類別基於最新的 <b>Spring Cloud Gateway Server WebMVC</b> 規格實作。
 * 放棄了容易因縮排、隱形空白或版本解析產生盲點的傳統 YAML 配置，全面改用強型別、編譯期安全的 Java 程式碼鏈式定義。
 * </p>
 * <p>
 * <b>【架構相容適配】</b>：<br>
 * 因應新版架構斷崖式更新（Breaking Change），{@code http()} 處理器內部不再接受目標位址參數，
 * 統一改由前置過濾器 {@code .before(uri(...))} 執行目的地的解耦注入與動態轉發。
 * </p>
 * <p>
 * <b>【高可用性防禦】</b>：<br>
 * 內建 Resilience4j 斷路器 (Circuit Breaker)，針對後端微服務負載過高或崩潰時，
 * 實施毫秒級的快速失敗 (Fail-Fast) 與就地降級 (Fallback) 策略，阻斷雪崩效應。
 * </p>
 */
@Configuration
public class GatewayRoutesConfiguration {

	private final GatewayRateLimiterPort rateLimiter;

	public GatewayRoutesConfiguration(GatewayRateLimiterPort rateLimiter) {
		this.rateLimiter = rateLimiter;
	}

	@Bean
	public RouterFunction<ServerResponse> saasGatewayRoutes() {

		// 1. 先定義所有的路由規則
		RouterFunction<ServerResponse> routes = route("auth-public-route")
				.route(path("/api/auth/**"), http())
				// [精準狙擊]：針對註冊/登入特別嚴格 (10秒 3次)
				.filter(ipRateLimiter(rateLimiter, "auth-public", 3, 10))
				.filter(circuitBreaker("authCircuitBreaker", URI.create("forward:/fallback/auth")))
				.before(uri("http://localhost:8080"))
				.build()

				/*
				 * ===================================================================
				 * 2. 認證中心管理/內部商務通道 (Auth Service - Admin Management)
				 * ===================================================================
				 * 涵蓋：使用者、角色、群組之增刪改查管理
				 * 安全策略：強制經過 JWT 過濾器校驗，並壓入多租戶上下文與權限。
				 * 崩潰防護：綁定 authCircuitBreaker 熔斷器。
				 */
				.and(route("auth-admin-route")
						.route(path("/api/users/**")
								.or(path("/api/roles/**"))
								.or(path("/api/groups/**")), http())
						.filter(ipRateLimiter(rateLimiter, "dept-api", 10, 10))
						// SCG WebMVC 最新寫法：直接傳入 (斷路器實例 ID, 降級轉發 URI)
						.filter(circuitBreaker("authCircuitBreaker", URI.create("forward:/fallback/auth")))
						.before(uri("http://localhost:8080"))
						.build())

				/*
				 * ===================================================================
				 * 3. 部門組織架構微服務通道 (Department Service - Domain Root)
				 * ===================================================================
				 * 涵蓋：/api/departments/** 樹狀結構建立、撤銷、改組與時光機操作
				 * 轉發目標：精準投射至獨立部署於 8081 Port 的部門數據大腦。
				 * 崩潰防護：綁定 deptCircuitBreaker 熔斷器。
				 */
				.and(route("department-service-route")
						.route(path("/api/departments/**"), http())
						.filter(ipRateLimiter(rateLimiter, "dept-api", 2, 10))
						// SCG WebMVC 最新寫法：直接傳入 (斷路器實例 ID, 降級轉發 URI)
						.filter(circuitBreaker("deptCircuitBreaker", URI.create("forward:/fallback/department")))
						.before(uri("http://localhost:8081"))
						.build());
		// ===================================================================
		// 全局兜底防護網 (Global Filter)
		// ===================================================================
		// 將上面組合好的 routes，在最外層再包上一層限流器。
		// 這代表「所有進來網關的流量」，都會先經過這個每秒 50 次的寬鬆盤查，
		// 通過後，才會進入內部各自的精準限流與斷路器。
		return routes.filter(ipRateLimiter(rateLimiter, "global-baseline", 50, 1));
	}
}