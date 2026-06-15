package com.example.demo.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * <h2>[網關層] 全局路由矩陣宣告配置類別 (Type-Safe 絕對防禦版)</h2>
 * <p>
 * 本配置類別基於最新的 <b>Spring Cloud Gateway Server WebMVC</b> 規格實作。
 * 放棄了容易因縮排、隱形空白或版本解析產生盲點的傳統 YAML 配置，全面改用強型別、編譯期安全的 Java 程式碼鏈式定義。
 * </p>
 * <p>
 * <b>【架構相容適配】</b>：<br>
 * 因應新版架構斷崖式更新（Breaking Change），{@code http()} 處理器內部不再接受目標位址參數， 統一改由前置過濾器
 * {@code .before(uri(...))} 執行目的地的解耦注入與動態轉發。
 * </p>
 */
@Configuration
public class GatewayRoutesConfiguration {

	/**
	 * 宣告核心多租戶微服務拓樸路由矩陣。
	 * <p>
	 * 整合了認證中心的公共寬限通道、認證管理端商務通道、以及 8081 獨立運作的部門領域聚合根通道。
	 * </p>
	 *
	 * @return 封裝完整轉發邏輯與路徑斷言（Predicates）的 {@link RouterFunction} 路由樹
	 */
	@Bean
	public RouterFunction<ServerResponse> saasGatewayRoutes() {
		return route("auth-public-route")
				/**
				 * 1. 認證中心公共通道 (Auth Service - Public Pass)
				 * 
				 * <pre>
				 * 涵蓋：/api/auth/login, /api/auth/register 
				 * 路由策略：直接放行轉發，流量至後端 AP 進行無狀態簽發 Token。
				 * </pre>
				 */
				.route(path("/api/auth/**"), http()).before(uri("http://localhost:8080")).build()

				/**
				 * 2.認證中心管理/內部商務通道 (Auth Service - Admin Management)
				 * 
				 * <pre>
				 * 涵蓋：使用者、角色、群組之增刪改查管理
				 * 安全策略：強制經過 JWT 過濾器校驗，並壓入多租戶上下文與權限。
				 * </pre>
				 */
				.and(route("auth-admin-route")
						.route(path("/api/users/**").or(path("/api/roles/**")).or(path("/api/groups/**")), http())
						.before(uri("http://localhost:8080")).build())

				/**
				 * 3. 部門組織架構微服務通道 (Department Service - Domain Root)
				 * 
				 * <pre>
				 * 涵蓋：/api/departments/** 樹狀結構建立、撤銷、改組與時光機操作 
				 * 轉發目標：精準投射至獨立部署於 8081 Port 的部門數據大腦。
				 * </pre>
				 */
				.and(route("department-service-route").route(path("/api/departments/**"), http())
						.before(uri("http://localhost:8081")).build());
	}
}