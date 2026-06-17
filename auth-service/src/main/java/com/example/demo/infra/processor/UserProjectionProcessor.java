package com.example.demo.infra.processor;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.domain.user.event.UserChangedEvent;
import com.example.demo.application.shared.event.TenantEventEnvelope;
import com.example.demo.infra.projection.repository.UserViewRepository;
import com.example.demo.infra.projection.view.UserView;

/**
 * <h2>[讀取側 - 背景異步] 使用者 CQRS 視圖投影處理器</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本處理器為 {@code user_view} 展示櫥窗的非同步裝潢工。 負責將使用者擁有的多個角色，壓縮並摺疊為 CSV
 * 逗號分隔欄位，徹底終結後台分頁查詢時的 {@code N+1} 效能地獄。
 * </p>
 */
@Component
public class UserProjectionProcessor {

	private final UserViewRepository viewRepository;

	public UserProjectionProcessor(UserViewRepository viewRepository) {
		this.viewRepository = viewRepository;
	}

	@Async // 🚀 開闢獨立異步執行緒
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 🚀 寫入端安全提交後啟動
	@Transactional(propagation = Propagation.REQUIRES_NEW) // 🚀 獨立全新事務開道
	public void processProjection(TenantEventEnvelope envelope) {
		if (!(envelope.event() instanceof UserChangedEvent event)) {
			return;
		}

		String tenantId = envelope.tenantId();
		UUID userId = event.userId();

		try {
			// CQRS 摺疊精髓：將角色 Set 直接拉平成 CSV 字串
			String rolesCsv = String.join(",", event.roles());

			// 實作視圖 Upsert 同步
			UserView userView = viewRepository.findByTenantIdAndId(tenantId, userId)
					.map(existingView -> new UserView(existingView.getId(), existingView.getTenantId(),
							event.username(), event.email(), event.status(), rolesCsv))
					.orElseGet(() -> new UserView(userId, tenantId, event.username(), event.email(), event.status(),
							rolesCsv));

			viewRepository.save(userView); // 直擊投影表

		} catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
			// 🚀 併發防禦：高併發撞車直接溫和跳過，交給隨後跟上的 Outbox 排程重試進行自我修復
			System.err.printf(
					"[Concurrent Update Ignored] User view sync conflict for user: %s, will be healed by outbox retry.%n",
					userId);
		} catch (Exception e) {
			System.err.printf("[Projection Failed] Critical error syncing user view: %s, Error: %s%n", userId,
					e.getMessage());
		}
	}
}