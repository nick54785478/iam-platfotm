package com.example.demo.infra.context;

/**
 * <h2>[應用層 - 上下文] 多租戶執行緒上下文容器 (Tenant Context)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為 SaaS 多租戶架構的「空氣管道」。它利用 Java 的 {@link ThreadLocal} 機制， 確保在 Tomcat/Undertow
 * 執行緒池中，每一個獨立的 HTTP 請求執行緒都擁有一份<b>完全隔離、互不干擾</b>的租戶識別碼（Tenant ID）。
 * </p>
 * <p>
 * <b>【硬核安全防線 - 記憶體洩漏與數據污染防禦】</b>：<br>
 * 由於現代 Web 容器（如 Spring Boot 內建的 Tomcat）採用「執行緒池重用機制」（Thread Pool Reuse），
 * 如果請求結束時沒有手動調用 {@link #clear()}，該執行緒在服務下一個全然不同的客戶時， 就會<b>攜帶上一個用戶的 Tenant
 * ID</b>，從而引發恐怖的 SaaS 跨租戶數據越權污染災難。因此必須強制在攔截器閉環清空。
 * </p>
 */
public final class TenantContext {

	/**
	 * 核心隔離容器：以 ThreadLocal 守護每個 HTTP 請求執行緒獨立的租戶標籤
	 */
	private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

	/** 靜態工具類別，鎖死建構式，防止外部無意義實例化 */
	private TenantContext() {
	}

	/**
	 * <b>獲取當前執行緒鎖定的租戶識別碼</b>
	 * <p>
	 * 供 Infrastructure 側的所有 Adapter、Repository（如 UserWriterAdapter）在存取資料庫時，
	 * 自動無感地調用此方法來進行 {@code tenant_id} 的 SQL 條件拼接，守住物理隔離防線。
	 * </p>
	 * 
	 * @return 當前租戶識別碼字串，例如 "tenant_comp_01"
	 */
	public static String getCurrentTenantId() {
		return CURRENT_TENANT.get();
	}

	/**
	 * <b>動態設定當前執行緒的租戶識別碼</b>
	 * <p>
	 * 僅限最外圈的安全攔截器（TenantInterceptor）或非同步異步線程處理器在入口端調用。
	 * </p>
	 */
	public static void setCurrentTenantId(String tenantId) {
		CURRENT_TENANT.set(tenantId);
	}

	/**
	 * <b>極度重要：強制清空當前執行緒的租戶狀態</b>
	 * <p>
	 * 當整個 HTTP 請求完全結束、TCP 準備放回執行緒池前，必須被死死調用， 徹底拔除 ThreadLocal
	 * 中的內存引用，防禦記憶體洩漏與跨租戶數據污染地雷。
	 * </p>
	 */
	public static void clear() {
		CURRENT_TENANT.remove();
	}
}