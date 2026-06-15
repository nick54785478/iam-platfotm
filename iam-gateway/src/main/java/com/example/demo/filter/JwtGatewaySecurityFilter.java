package com.example.demo.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <h2>[網關層] 分布式無狀態鑑權與權限下穿全局過濾器</h2>
 * <p>
 * <b>【職責定位】</b>：本元件充當整個微服務叢集的最外圈「護城河守門員」，基於 Servlet Tomcat 容器運作。
 * </p>
 * <p>
 * <b>【核心防禦演算法】</b>：<br>
 * 1. <b>零 DB 密碼學解密</b>：利用本地快取之對稱金鑰（HMAC256），以微秒級速度就地解開 JWT，拒絕非授權直擊。<br>
 * 2. <b>權限下穿機制（Data Injection）</b>：解密成功後，將高價值的
 * {@code username}、{@code tenant}（租戶標籤）、 及 {@code authorities}（權限陣列）扁平化為無狀態
 * HTTP Headers 重新 Mutate 注入 Request 體內， 使下游微服務（如 DeptService）達成「零解密依賴、僅盲從網關
 * Header」的極致效能架構。<br>
 * </p>
 *
 * @author YourName
 * @since 2026-06-15
 */
@Component
public class JwtGatewaySecurityFilter extends OncePerRequestFilter implements Ordered {

	private final Algorithm algorithm;

	/**
	 * 建構子：從 Spring 環境變數動態載入對稱加密私鑰。
	 *
	 * @param secret 與安全中心對齊的 JWT 密鑰字串
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

		logger.info("進入 Gateway Security Filter");
		String path = request.getRequestURI();

		// 1. 公共豁免白名單通道：登入與註冊直接放行，讓流量安全穿透去後端 AP 換取新 Token
		if (path.contains("/api/auth/login") || path.contains("/api/auth/register")) {
			System.out.println("[SCG] 公共豁免通道放行：" + path);
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

			// [合約欄位校準]：精準對齊 AuthService 簽發的 Claim 標籤 "tenant"
			String tenantId = jwt.getClaim("tenant").asString();
			List<String> authorities = jwt.getClaim("authorities").asList(String.class);

			// 4. 【核心黑科技：權限下穿 Wrapper 封裝】
			// 因 Servlet Request 預設不可變，透過自訂的類別包裝，將解開的元數據強行化身為標準 HTTP Headers
			MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(request);
			mutableRequest.putHeader("X-User-Id", username);
			mutableRequest.putHeader("X-Tenant-Id", tenantId);
			mutableRequest.putHeader("X-User-Permissions", String.join(",", authorities));

			// 5. 攜帶完全對齊、去重、且滿血的 Headers 順暢穿透網關，派發給下游目標微服務
			filterChain.doFilter(mutableRequest, response);

		} catch (Exception e) {
			// Token 過期、雜湊值不對、簽章損毀，一律格殺勿論
			respondWith401(response, "Token verification failed or expired. Reason: " + e.getMessage());
		}
	}

	/**
	 * 當場中斷 HTTP 鏈路並回傳標準的 JSON 401 結構。
	 *
	 * @param response 當前 HTTP 響應
	 * @param message  錯誤敘述
	 */
	private void respondWith401(HttpServletResponse response, String message) throws IOException {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
	}

	/**
	 * 定義本過濾器在 Tomcat FilterChain 中的絕對權重。
	 *
	 * @return 最高優先權常數，確保身分驗證發生在所有業務處理與路由重定向之前
	 */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	// =========================================================================
	// 內部類別：HttpServletRequestWrapper (WebMVC 竄改 Header 必備黑科技)
	// =========================================================================

	/**
	 * <h2>可變 HTTP 請求包裝器 (基於 RFC 7230 標準化修正版)</h2>
	 * <p>
	 * <b>【核心痛點排除說明】</b>：<br>
	 * 1. <b>大小寫去重防線</b>：Java 預設的 {@link HashMap} 嚴格區分大小寫，而 HTTP 協議規定 Header
	 * 名稱不區分大小寫。 若直接使用 HashMap，轉發過程中會被 Spring 與 Tomcat 解析出大寫 {@code X-Tenant-Id} 與小寫
	 * {@code x-tenant-id} 共存的雙胞胎分身，進而在下游引發 {@code WPG,WPG} 的數據串接混亂或 400 Bad Request
	 * 暴斃。<br>
	 * 2. <b>物理去重方案</b>：內部底層容器全面升級為帶有 {@link String#CASE_INSENSITIVE_ORDER} 的
	 * {@link TreeMap} 與
	 * {@link TreeSet}。不論外部透過何種姿勢（大小寫組合）查詢、注入、或尋找名稱，皆能保持全局宇宙唯一的單一值對齊。
	 * </p>
	 */
	private static class MutableHttpServletRequest extends HttpServletRequestWrapper {

		/**
		 * 忽略大小寫之自訂數據容器槽
		 */
		private final Map<String, String> customHeaders;

		/**
		 * 建立一個全新的可變 Request 殼子。
		 *
		 * @param request 原始不可變的 HttpServletRequest
		 */
		public MutableHttpServletRequest(HttpServletRequest request) {
			super(request);
			// 強制綁定忽略大小寫比較器，根除 WPG,WPG 分身災難
			this.customHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		}

		/**
		 * 注入一個自訂的 HTTP Header。若存在同名 Header (忽略大小寫)，將直接強力覆蓋。
		 *
		 * @param name  Header 鍵名 (例如 X-Tenant-Id)
		 * @param value 注入之文本值
		 */
		public void putHeader(String name, String value) {
			this.customHeaders.put(name, value);
		}

		/**
		 * 覆寫單一 Header 查詢。優先從自訂槽抓取， fallback 回原始請求。
		 */
		@Override
		public String getHeader(String name) {
			String headerValue = customHeaders.get(name);
			if (headerValue != null) {
				return headerValue;
			}
			return super.getHeader(name);
		}

		/**
		 * 覆寫全局 Header 名稱枚舉合併。
		 * <p>
		 * 核心利用 {@link TreeSet} 的大小寫不敏感去重能力，將自訂 Header 與 Tomcat 原生 Header 融合成唯一集合，
		 * 徹底根除下游微服務 {@code MissingRequestHeaderException} 的隱形炸彈。
		 * </p>
		 */
		@Override
		public Enumeration<String> getHeaderNames() {
			// 強制將合併集合換成不區分大小寫的 TreeSet
			Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

			// 1. 塞入網關下穿的滿血高價值 Headers
			set.addAll(customHeaders.keySet());

			// 2. 疊加原始前端帶來的其餘 Headers (如 User-Agent, Host)，重複者因 TreeSet 特性會被自動抹除
			Enumeration<String> e = super.getHeaderNames();
			while (e.hasMoreElements()) {
				set.add(e.nextElement());
			}
			return Collections.enumeration(set);
		}

		/**
		 * 覆寫複數 Header 數組查詢，確保透過動態轉發代理（Proxy）複製標頭時，能正確吐出下穿的值。
		 */
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