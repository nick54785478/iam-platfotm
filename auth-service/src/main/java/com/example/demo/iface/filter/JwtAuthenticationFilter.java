//package com.example.demo.iface.filter;
//
//import java.io.IOException;
//import java.util.List;
//
//import org.springframework.stereotype.Component;
//
//import com.example.demo.application.port.TokenProviderPort;
//
//import jakarta.servlet.Filter;
//import jakarta.servlet.FilterChain;
//import jakarta.servlet.ServletException;
//import jakarta.servlet.ServletRequest;
//import jakarta.servlet.ServletResponse;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//
///**
// * <h2>[基礎設施層 - 安全過濾器] 全局身份守門員 - 基礎設施全面通航版</h2>
// * <p>
// * <b>【綠色通道聯防】</b>：<br>
// * 本元件已內聚整合了 Swagger API 檔案、Prometheus 效能指標監控（Actuator）、以及 H2-Console
// * 內嵌資料庫的全部路由豁免權， 確保運維管線與開發者工具在最外圈安全穿透，不破壞技術基建的運作。
// * </p>
// */
//@Component
//public class JwtAuthenticationFilter implements Filter {
//
//	private final TokenProviderPort tokenProviderPort;
//
//	// 🚀 建立高雅的「前綴匹配白名單」矩陣
//	private static final List<String> EXCLUDED_PREFIXES = List.of("/api/auth/login", // 🟢 匿名登入
//			"/api/auth/register", // 🟢 匿名註冊
//			"/actuator", // 📊 Prometheus / Actuator 監控端點全系列放行
//			"/swagger-ui", // 📝 Swagger UI 網頁靜態資源
//			"/v3/api-docs", // 📝 OpenAPI 3.0 規格 JSON 輸出點
//			"/h2-console" // 🗄️ H2 記憶體資料庫後台面板
//	);
//
//	public JwtAuthenticationFilter(TokenProviderPort tokenProviderPort) {
//		this.tokenProviderPort = tokenProviderPort;
//	}
//
//	@Override
//	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
//			throws IOException, ServletException {
//
//		HttpServletRequest httpRequest = (HttpServletRequest) request;
//		HttpServletResponse httpResponse = (HttpServletResponse) response;
//
//		// 🚀 自動剝離 server.servlet.context-path 雜訊，拿到乾淨的核心 Servlet 路由
//		String path = httpRequest.getServletPath();
//
//		// 🔥 【核心黑科技】：利用 Stream 快速判定當前請求是否命中任何一個綠色通道前綴
//		boolean isExcluded = EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)
//				|| EXCLUDED_PREFIXES.stream().anyMatch(httpRequest.getRequestURI()::contains);
//		if (isExcluded) {
//			chain.doFilter(request, response); // 🟢 綠色通道直接無痛放行，不進行任何 Token 校驗
//			return;
//		}
//
//		// 2. 關係型 Bearer Token 令牌拔取
//		String authHeader = httpRequest.getHeader("Authorization");
//		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//			respondWith401(httpResponse, "Missing or invalid Authorization header.");
//			return; // ❌ 擊落非法訪問
//		}
//
//		String token = authHeader.substring(7);
//
//		try {
//			// 3. 密碼學無狀態校驗
//			String username = tokenProviderPort.extractUsername(token);
//			httpRequest.setAttribute("JWT_HEX_USERNAME", username);
//
//			// 🟢 4. 驗證通過，推進至內圈 Interceptor
//			chain.doFilter(request, response);
//
//		} catch (Exception e) {
//			respondWith401(httpResponse, "Token verification failed: " + e.getMessage());
//		}
//	}
//
//	private void respondWith401(HttpServletResponse response, String message) throws IOException {
//		response.setContentType("application/json;charset=UTF-8");
//		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//		response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
//	}
//}