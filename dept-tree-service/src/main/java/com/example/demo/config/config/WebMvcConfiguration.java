package com.example.demo.config.config;

import com.example.demo.iface.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import security.interceptor.PermissionGuardInterceptor;

/**
 * <h2>[基礎設施層 - 配置] Spring MVC 全局攔截器路由設定 (Web Mvc Configuration)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本配置類別負責將我們寫好的攔截器安全地編織進 Spring 的 Web 路由過濾網中。
 * 定義哪些路徑是必須嚴格防禦的多租戶商務區，哪些路徑是允許公開訪問的公共綠色通道。
 * </p>
 * <p>
 * <b>【攔截器管線順序】</b>：<br>
 * 1. <b>PermissionGuardInterceptor</b>: 優先執行。執行無狀態的動態權限校驗，落實 Fail-Fast 阻擋越權存取。<br>
 * 2. <b>TenantInterceptor</b>: 權限通過後才執行。負責將 X-Tenant-Id 綁定至 ThreadLocal 上下文。
 * </p>
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

	private final PermissionGuardInterceptor permissionGuardInterceptor;
	private final TenantInterceptor tenantInterceptor;

	// 透過建構子注入兩個攔截器
	public WebMvcConfiguration(
			PermissionGuardInterceptor permissionGuardInterceptor,
			TenantInterceptor tenantInterceptor) {
		this.permissionGuardInterceptor = permissionGuardInterceptor;
		this.tenantInterceptor = tenantInterceptor;
	}

	/**
	 * <b>註冊並調配攔截器路由管線</b>
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {

		// ===================================================================
		// 定義共用的綠色通道 (白名單)
		// ===================================================================
		String[] excludePatterns = {
				"/api/public/**",
				"/api/auth/login",
				"/api/auth/register",
				"/actuator/**",     // 放行 Prometheus 刮取資料
				"/swagger-ui/**",   // 放行 Swagger 網頁
				"/v3/api-docs/**",  // 放行 OpenAPI JSON
				"/h2-console/**"    // 放行 H2 控制台
		};

		// ===================================================================
		// 管線 1：動態權限攔截器 (先執行，快速擋下非法流量)
		// ===================================================================
		registry.addInterceptor(permissionGuardInterceptor)
				.addPathPatterns("/api/**")
				.excludePathPatterns(excludePatterns);

		// ===================================================================
		// 管線 2：多租戶上下文攔截器 (後執行，權限合法者才給予建立上下文)
		// ===================================================================
		registry.addInterceptor(tenantInterceptor)
				.addPathPatterns("/api/**")
				.excludePathPatterns(excludePatterns);
	}
}