package com.example.demo.iface.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.example.demo.infra.context.TenantContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <h2>[基礎設施層 - Web 適配器] 多租戶安全過濾攔截器 (Tenant Interceptor)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本元件扮演最外圈的「邊境邊防官」。它在任何 HTTP 請求直擊 Controller 業務層之前， 優先從請求的元數據（如 HTTP
 * Header）中剝離出租戶標籤，並將其注入 {@link TenantContext} 中通電運作。
 * </p>
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

	/** 企業級 SaaS 的標準 HTTP Header 租戶標記鍵名 */
	private static final String TENANT_HEADER = "X-Tenant-ID";

	/**
	 * <b>【前置攔截 - 預檢防禦】在 Controller 執行之前硬核觸發</b>
	 * 
	 * @return 若放行回傳 {@code true}；若無效或漏傳則直接在中途「擊落請求」回傳 {@code false}
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		// 1. 從前端傳入的 Header 拔出本次請求宣稱的租戶
		String headerTenantId = request.getHeader(TENANT_HEADER);

		if (headerTenantId == null || headerTenantId.isBlank()) {
			response.setContentType("application/json;charset=UTF-8");
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			response.getWriter().write("{\"error\": \"Missing X-Tenant-ID header.\"}");
			return false;
		}

		// 🚀 2. 驚艷的雙重夾擊聯防：
		// 由於公共登入通道已經被 Filter 放行，能走到這裡的請求，體內必定已經被 JwtFilter 注入了 "JWT_HEX_TENANT_ID"
		String jwtTenantId = (String) request.getAttribute("JWT_HEX_TENANT_ID");

		// [⚠️ 越權硬核攔截]：如果前端 Header 帶的是 tenant_B，但拿的 Token 卻是屬於 tenant_A 的
		// 說明這是一次蓄意的跨租戶越權篡改攻擊！當場沒收令牌，重罰 403 Forbidden！
		if (jwtTenantId != null && !jwtTenantId.equals(headerTenantId)) {
			response.setContentType("application/json;charset=UTF-8");
			response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403 Forbidden
			response.getWriter().write("{\"error\": \"Security breach! Token tenant does not match request tenant.\"}");
			return false;
		}

		// 3. 通過安全交叉核對，ThreadLocal 正式接通電源
		TenantContext.setCurrentTenantId(headerTenantId);
		return true;
	}

	/**
	 * <b>【後置清理 - 安全閉環】在整個請求完全結束後執行</b>
	 * <p>
	 * 不論業務處理是成功（200 OK）還是中途噴出崩潰異常（500 Internal Error）， 哪怕 View
	 * 渲染完畢、客戶端已中斷連接，此方法都<b>必定會被保證執行（相當於 finally 區塊）</b>。
	 * </p>
	 */
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
			throws Exception {
		// 🚀 核心一體化閉環：強制擦除當前執行緒的殘留租戶身份，乾乾淨淨地將執行緒還回池中
		TenantContext.clear();
	}
}