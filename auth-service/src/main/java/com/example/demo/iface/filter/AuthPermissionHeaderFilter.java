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
 * <h2>[基礎設施層 - 安全過濾器] 微服務無狀態權限檢驗器 (Permission Header Filter)</h2>
 * <p>
 * <b>【架構對齊與降維打擊】</b>：<br>
 * 本微服務（Auth Service）已與前端的 SCG（Spring Cloud Gateway）達成完美的職責解耦。 本地端點不再負責消耗 CPU
 * 算力進行 JWT 的密碼學解密（Authentication），而是全面退化為純粹的授權（Authorization）防線。
 * </p>
 * <p>
 * <b>【零信任網路防禦】</b>：<br>
 * 只盲目信任並校驗由網關下穿的 {@code X-User-Permissions} HTTP Headers。 徹底實現極致的 $O(1)$
 * 效能校驗與零密碼學依賴，同時防止駭客直擊 8080 Port 造成的繞過攻擊。
 * </p>
 */
@Component
public class AuthPermissionHeaderFilter implements Filter {

	/**
	 * 綠色通道矩陣 (Whitelist)
	 * <p>
	 * 包含系統登入/註冊入口，以及 DevOps 監控與開發者文件端點。這些路徑允許匿名流量直接穿透。
	 * </p>
	 */
	private static final List<String> EXCLUDED_PREFIXES = List.of("/api/auth/login", "/api/auth/register", "/actuator",
			"/swagger-ui", "/v3/api-docs", "/h2-console");

	/**
	 * 執行核心 HTTP 請求過濾與權限校驗。
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;
		HttpServletResponse httpResponse = (HttpServletResponse) response;
		String path = httpRequest.getRequestURI();

		// 1. 綠色通道比對：若命中白名單，直接放行交由 Spring MVC 處理
		boolean isExcluded = EXCLUDED_PREFIXES.stream().anyMatch(path::contains);
		if (isExcluded) {
			chain.doFilter(request, response);
			return;
		}

		/*
		 * 2. 降維打擊（Header 校驗）： 不再拔取 Bearer Token，而是直接讀取 SCG 網關解密後塞進來的「權限 Header」。 此舉為典型的
		 * Zero-Trust 防護：如果連這個 Header 都沒有，代表流量未經網關洗禮，當場擊落！
		 */
		String permissionsHeader = httpRequest.getHeader("X-User-Permissions");
		// System.out.println("permissionHeader:" + permissionsHeader); // 可依據環境切換為
		// log.debug

		if (permissionsHeader == null || permissionsHeader.isBlank()) {
			respondWithJson(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
					"Missing Gateway Routing Headers. Access strictly via API Gateway only.");
			return;
		}

		// 3. 將逗號分隔的扁平化權限字串，還原為 List 集合
		List<String> authorities = Arrays.asList(permissionsHeader.split(","));

		/*
		 * 4. 【戰術防禦硬核鎖死】：RBAC 角色基礎存取控制 判定該請求攜帶的權限池中，是否具備存取 Auth Service 管理端點的合法權利。 (具備
		 * ADMIN_ALL 或是 STAFF_READ 其中之一即視為合法)
		 */
		boolean isAuthorized = authorities.contains("auth-service:ADMIN_ALL")
				|| authorities.contains("auth-service:STAFF_READ");

		if (!isAuthorized) {
			// 身分雖然是合法的 (過得了網關)，但「權限不配」，直接重罰 403 Forbidden
			respondWithJson(httpResponse, HttpServletResponse.SC_FORBIDDEN,
					"Access Denied: You do not have permission to access AuthService endpoints.");
			return;
		}

		/*
		 * 5. 權限校驗通過，放行進入 Controller！ (備註：關於 X-Tenant-Id 與 X-User-Id 的攔截與綁定，已交由內層的
		 * TenantInterceptor 或 Controller 參數負責處理)
		 */
		chain.doFilter(request, response);
	}

	/**
	 * 當場中斷 HTTP 鏈路並回傳標準的 JSON 錯誤結構。
	 *
	 * @param response 當前 HTTP 響應
	 * @param status   HTTP 狀態碼 (如 401, 403)
	 * @param message  錯誤敘述
	 */
	private void respondWithJson(HttpServletResponse response, int status, String message) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(status);
		response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
	}
}