package com.example.demo.iface.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


/**
 * <h2>[網關層] 分布式無狀態鑑權與權限下穿全局過濾器</h2>
 * <p>
 * <b>【職責定位】</b>：本元件充當整個微服務叢集的最外圈「護城河守門員」，基於 Servlet Tomcat 容器運作。
 * </p>
 * <p>
 * <b>【核心防禦演算法】</b>：<br>
 * 1. <b>AntPathMatcher 白名單放行</b>：支援精準與萬用字元匹配，安全放行基礎設施與公開端點。<br>
 * 2. <b>零 DB 密碼學解密</b>：利用本地快取之對稱金鑰（HMAC256），以微秒級速度就地解開 JWT，拒絕非授權直擊。<br>
 * 3. <b>權限下穿機制（Data Injection）</b>：解密成功後，將高價值的
 * {@code username}、{@code tenant} 及 {@code authorities} 扁平化為無狀態 HTTP Headers，
 * 重新 Mutate 注入 Request 體內，使下游微服務達成「零解密依賴」的極致效能架構。
 * </p>
 */
@Component
public class JwtGatewaySecurityFilter extends OncePerRequestFilter implements Ordered {

	private final Algorithm algorithm;

	// 引入 Spring 內建的 Ant 路由匹配器，支援 /** 語法
	private final AntPathMatcher pathMatcher = new AntPathMatcher();

	// 定義全局公共豁免白名單 (Public Whitelist)
	private static final List<String> PUBLIC_WHITELIST = List.of(
			// 1. Auth 認證通道
			"/api/auth/login",
			"/api/auth/register",

			// 2. SaaS 租戶註冊通道 (相容新舊版本 API 路徑)
			"/api/v1/platform/tenants",
			"/api/platform/tenants",

			// 3. 基礎設施與監控通道 (Prometheus)
			"/actuator/prometheus",
			"/actuator/health",

			// 4. H2 資料庫控制台
			"/h2-console/**",

			// 5. Swagger / OpenAPI 3.0 UI 靜態資源與 API 文件
			"/v3/api-docs/**",
			"/swagger-ui/**",
			"/swagger-ui.html",
			"/swagger-resources/**",
			"/webjars/**"
	);

	/**
	 * 建構子：從 Spring 環境變數動態載入對稱加密私鑰。
	 */
	public JwtGatewaySecurityFilter(@Value("${app.jwt.secret}") String secret) {
		this.algorithm = Algorithm.HMAC256(secret);
	}

	/**
	 * 執行核心過濾攔截與身分下穿邏輯。
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String path = request.getRequestURI();

		// 1. 🛡️ 執行白名單校驗：只要有任何一個 pattern 匹配當前路徑，即刻放行
		boolean isWhitelisted = PUBLIC_WHITELIST.stream()
				.anyMatch(pattern -> pathMatcher.match(pattern, path));

		if (isWhitelisted) {
			logger.debug("[SCG] 公共白名單通道放行：" + path);
			filterChain.doFilter(request, response);
			return;
		}

		// 2. 拔取 HTTP Authorization 標頭
		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			respondWith401(response, "Missing or invalid Authorization header.");
			return; // ❌ 缺失憑證，中途強制擊落
		}

		// 裁切出純粹的密碼學 Token 文本
		String token = authHeader.substring(7);

		try {
			// 3. 執行自解密與合約校準
			DecodedJWT jwt = JWT.require(algorithm).build().verify(token);

			String username = jwt.getSubject();
			String tenantId = jwt.getClaim("tenant").asString();
			List<String> authorities = jwt.getClaim("authorities").asList(String.class);

			// 4. 【核心黑科技：權限下穿 Wrapper 封裝】
			MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(request);
			mutableRequest.putHeader("X-User-Id", username);
			mutableRequest.putHeader("X-Tenant-Id", tenantId);

			// 防呆：確保權限不為空再進行字串拼接
			if (authorities != null && !authorities.isEmpty()) {
				mutableRequest.putHeader("X-User-Permissions", String.join(",", authorities));
			} else {
				mutableRequest.putHeader("X-User-Permissions", "");
			}

			// 5. 攜帶滿血 Headers 順暢穿透網關
			filterChain.doFilter(mutableRequest, response);

		} catch (Exception e) {
			// Token 過期、雜湊值不對、簽章損毀，一律格殺勿論
			respondWith401(response, "Token verification failed or expired. Reason: " + e.getMessage());
		}
	}

	private void respondWith401(HttpServletResponse response, String message) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	// =========================================================================
	// 內部類別：MutableHttpServletRequest (維持你原本的完美設計，未做更動)
	// =========================================================================
	private static class MutableHttpServletRequest extends HttpServletRequestWrapper {

		private final Map<String, String> customHeaders;

		public MutableHttpServletRequest(HttpServletRequest request) {
			super(request);
			this.customHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		}

		public void putHeader(String name, String value) {
			this.customHeaders.put(name, value);
		}

		@Override
		public String getHeader(String name) {
			String headerValue = customHeaders.get(name);
			if (headerValue != null) {
				return headerValue;
			}
			return super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			set.addAll(customHeaders.keySet());

			Enumeration<String> e = super.getHeaderNames();
			while (e.hasMoreElements()) {
				set.add(e.nextElement());
			}
			return Collections.enumeration(set);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			String headerValue = customHeaders.get(name);
			if (headerValue != null) {
				return Collections.enumeration(Collections.singletonList(headerValue));
			}
			return super.getHeaders(name);
		}
	}
}