package com.example.demo.config.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.demo.iface.interceptor.TenantInterceptor;
import security.interceptor.PermissionGuardInterceptor;

/**
 * <h2>[基礎設施層 - AuthService 配置] Spring MVC 全局攔截器路由設定 (Web Mvc Configuration)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本配置類別負責將我們寫好的 {@link TenantInterceptor} 安全地編織進 Spring 的 Web 路由過濾網中，
 * 定義哪些路徑是必須嚴格防禦的多租戶商務區，哪些路徑是允許公開訪問的公共綠色通道。
 * </p>
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

	private final TenantInterceptor tenantInterceptor;
	private final PermissionGuardInterceptor permissionGuardInterceptor;

	public WebMvcConfiguration(TenantInterceptor tenantInterceptor, PermissionGuardInterceptor permissionGuardInterceptor) {
		this.tenantInterceptor = tenantInterceptor;
        this.permissionGuardInterceptor = permissionGuardInterceptor;
    }

	/**
	 * <b>註冊並調配攔截器路由管線</b>
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(tenantInterceptor)
				// 雷達全開：強行保護所有以 /api/ 開頭的商務接口、管理接口與 CQRS 查詢讀寫端
				.addPathPatterns("/api/**")

				// 綠色通道放行：排除不需要租戶標籤的公共匿名訪問（如全局系統登入、靜態驗證碼、健康檢查介面）
				.excludePathPatterns(
						"/api/public/**",
						"/api/auth/login",
						"/api/auth/register",
						"/actuator/**", // 放行 Prometheus 刮取資料
						"/swagger-ui/**", // 放行 Swagger 網頁
						"/v3/api-docs/**", // 放行 OpenAPI JSON
						"/h2-console/**" // 放行 H2 控制台
				);

		registry.addInterceptor(permissionGuardInterceptor)
				.addPathPatterns("/**") // 攔截所有路徑
				// 排除不需攔截的靜態資源或 Swagger UI
				.excludePathPatterns("/swagger-ui/**", "/v3/api-docs/**", "/error");
	}
}