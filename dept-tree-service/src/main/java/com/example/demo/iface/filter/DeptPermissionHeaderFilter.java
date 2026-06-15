package com.example.demo.iface.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <h2>[基礎設施層] 部門服務 - 無狀態權限檢驗器 (Zero-Trust Security Filter)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 專職守護部署於 8081 Port 的部門微服務（Department Service）。 負責攔截企圖繞過 API
 * 網關（Gateway）直接攻擊後端的非法流量，並執行基於 Header 的權限校驗（RBAC）。
 * </p>
 * <p>
 * <b>【架構設計與邊界對齊】</b>：<br>
 * 遵循「網關負責認證 (Authentication)，微服務負責授權 (Authorization)」的微服務最佳實踐。 本服務不依賴 JWT
 * 密鑰，也不執行耗時的密碼學解密，而是完全信任並依賴 SCG 網關安全下穿的 {@code X-User-Permissions} 標頭。 達成微秒級的
 * O(1) 效能校驗。
 * </p>
 */
@Component
public class DeptPermissionHeaderFilter implements Filter {

	/**
	 * 執行核心 HTTP 請求過濾與授權校驗邏輯。
	 *
	 * @param request  Servlet 請求
	 * @param response Servlet 響應
	 * @param chain    過濾器鏈
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		String path = httpRequest.getRequestURI();

		/**
		 * 1. 基礎設施白名單 (Infrastructure Whitelist)
		 * 
		 * <pre>
		 * OpenAPI 文件、Swagger UI 面板以及 Actuator 狀態監控。 這些端點由內部運維管線或開發人員調用，無需業務權限校驗。
		 * </pre>
		 */
		if (path.contains("/v3/api-docs") || path.contains("/swagger-ui") || path.contains("/actuator")) {
			chain.doFilter(request, response);
			return;
		}

		/**
		 * 2. 防繞過物理防線 (Anti-Bypass Gateway Check)
		 * 
		 * <pre>
		 * Header。若為 null，表示該流量未經過 SCG 網關的 JWT 解密洗禮， 屬於駭客直擊 8081 端口的惡意請求，當場以 401 狀態碼擊落。
		 * </pre>
		 */
		String permissionsHeader = httpRequest.getHeader("X-User-Permissions");

		if (permissionsHeader == null || permissionsHeader.isBlank()) {
			respondWithJson(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
					"Access Denied: Missing Gateway Routing Headers. Direct access to this service is prohibited.");
			return;
		}

		/**
		 * 3. 領域授權校驗 (Domain Authorization & RBAC)
		 * 
		 * <pre>
		 * 將扁平化的權限字串還原為陣列，並檢查是否具備操作「部門領域聚合根」的合法權限。 
		 * 允許放行的條件：擁有超級管理員權限 (auth-service:ADMIN_ALL) 或 專屬的部門寫入權限 (dept-service:WRITE)。
		 * </pre>
		 */
		List<String> authorities = Arrays.asList(permissionsHeader.split(","));

		if (!authorities.contains("auth-service:ADMIN_ALL") && !authorities.contains("dept-service:WRITE")) {
			respondWithJson(httpResponse, HttpServletResponse.SC_FORBIDDEN,
					"Access Denied: Insufficient permissions for Department Service operations.");
			return;
		}

		/**
		 * 4. 放行通航 (Dispatch to Controller)
		 * 
		 * <pre>
		 * 權限合法，放行請求進入後端 Spring WebMVC 的 Controller 層。
		 * (此時 Controller 的 @RequestHeader 已能安全拔取 X-Tenant-Id 執行多租戶隔離)
		 * </pre>
		 */
		chain.doFilter(request, response);
	}

	/**
	 * 中斷 HTTP 鏈路並回傳標準化的 JSON 錯誤響應結構。
	 *
	 * @param response 當前 HTTP 響應
	 * @param status   HTTP 狀態碼 (如 401, 403)
	 * @param message  錯誤敘述與原因
	 */
	private void respondWithJson(HttpServletResponse response, int status, String message) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(status);
		response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
	}
}
