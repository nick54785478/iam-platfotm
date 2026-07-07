package com.example.demo.config.init;

import com.example.demo.application.service.RoleCommandService;
import com.example.demo.application.service.UserCommandService;
import com.example.demo.application.shared.command.CreateUserCommand;
import com.example.demo.infra.context.TenantContext;
import com.example.demo.infra.persistence.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>[基礎設施層 - 啟動器] 本地測試環境資料初始化引擎 (ApplicationRunner)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本元件掛載於 Spring Boot 生命週期末端。當 Spring Context 完全啟動、Tomcat 就緒後，將自動執行一次。
 * 專責於開發與本地測試環境中，注入多租戶的基礎測試資料（如管理員帳號、角色、權限）。
 * </p>
 * <p>
 * <b>【架構驅動與 Outbox 聯動】</b>：<br>
 * 揚棄傳統的 {@code data.sql} 直接塞資料庫作法，改為呼叫應用層的 {@code CommandService}。 此舉能完美走過 DDD
 * 聚合根的商業邏輯，並順利觸發實體內部的 Domain Events， 進而無縫激活底層的 <b>Outbox
 * Pattern（發件匣模式）</b>，確保資料與事件的最終一致性。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
// 強烈建議加上 @Profile({"local", "dev"})，避免這支程式在正式環境 (prod) 被意外喚醒！
// 如果你本地沒設 Profile，可以先暫時保持註解狀態
public class LocalDataInitializer implements ApplicationRunner {

	private final UserRepository userRepository;
	private final UserCommandService userCommandService;
	private final RoleCommandService roleCommandService;

	/**
	 * <b>啟動執行區塊：自帶 Spring 事務管理邊界</b>
	 * <p>
	 * 宣告 {@code @Transactional} 確保整個初始化過程（建立用戶、角色、綁定）都在同一個資料庫交易內。 若中途發生任何
	 * Exception，將整體 Rollback，避免產生半成品的測試資料。
	 * </p>
	 *
	 * @param args 應用程式啟動參數
	 */
	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		log.info("====== 開始初始化 SaaS 測試資料 (觸發 Outbox 同步) ======");

		// 1. 核心通電 (Context Binding)：手動模擬外部 HTTP 請求，強制綁定測試租戶空間
		// 讓底層 Hibernate 或 TenantInterceptor 在攔截時，能自動打上 tenant_id = 'WPG'
		TenantContext.setCurrentTenantId("WPG");



		try {
			/*
			 *  步驟 A：建立超級管理員帳號 (Trigger UserCreatedEvent)
			 */
			log.info("正在建立管理員帳號...");
			CreateUserCommand createUserCommand = new CreateUserCommand("V-NICK.GH.ZHANG", "password123",
					"V-NICK.GH.ZHANG@example.com");
			boolean exist = userRepository.existsByTenantIdAndUsername(TenantContext.getCurrentTenantId(), createUserCommand.username());
			if (exist) {
				return;
			}

			userCommandService.createUser(createUserCommand);

			// 步驟 B：建立系統角色與權限點 (RBAC 初始化)
			log.info("📦 正在建立 Roles 與 Permissions...");
			roleCommandService.createRole("管理員", "ADMIN");

			// 指派具體的系統操作權限點 (權限字串將成為 JWT 內 authorities 的一部分)
			roleCommandService.reportPermission("ADMIN", "*", "ADMIN_ALL", "Admin 登入");

			/*
			 * ========================================== 🔗 步驟 C：完成關聯綁定
			 * ==========================================
			 */
			log.info("正在將 ADMIN 角色綁定至使用者...");
			userCommandService.assignRoleToUser("V-NICK.GH.ZHANG", "ADMIN");

			log.info("====== 初始化完成！相關的 Domain Events 已成功寫入 Outbox 準備派發！ ======");
			log.info("測試帳號：V-NICK.GH.ZHANG / password123 (具備 ADMIN_ALL 最高權限)");
			log.info("租戶空間：WPG");

		} catch (Exception e) {
			log.error("測試資料初始化失敗，事務已 Rollback: {}", e.getMessage(), e);
			throw e; // 拋出異常以觸發 @Transactional Rollback
		} finally {
			// 3. 安全斷電 (Context Cleanup)：清理 ThreadLocal
			// 這是多執行緒環境下的鐵律，確保 Thread Pool 回收此執行緒時，不會將 WPG 租戶污染給下一個請求
			TenantContext.clear();
		}
	}
}