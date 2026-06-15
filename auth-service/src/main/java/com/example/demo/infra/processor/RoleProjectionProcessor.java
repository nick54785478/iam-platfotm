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

import com.example.demo.application.domain.role.event.RoleChangedEvent;
import com.example.demo.application.shared.event.TenantEventEnvelope;
import com.example.demo.infra.projection.repository.SpringDataRoleViewRepository;
import com.example.demo.infra.projection.view.RoleView;

import tools.jackson.databind.ObjectMapper;

/**
 * <h2>[讀取側 - 背景異步] 角色 CQRS 視圖投影處理器</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本處理器為 {@code role_view} 展示櫥窗的非同步裝潢工。 負責異步捕捉角色變更事件，將正規化結構拉平成 JSON 儲存，實現讀取側
 * $O(1)$ 的查詢效能。
 * </p>
 */
@Component
public class RoleProjectionProcessor {

	private final SpringDataRoleViewRepository viewRepository;
	private final ObjectMapper objectMapper;

	public RoleProjectionProcessor(SpringDataRoleViewRepository viewRepository, ObjectMapper objectMapper) {
		this.viewRepository = viewRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * 注意：此方法執行在【獨立背景執行緒】，主寫入線程的 ThreadLocal 隔離在這邊已失效。
	 */
	@Async // 開闢全新異步執行緒，完全不卡死主業務的 API 響應時間
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 🚀 只有寫入端主事務百分之百 Commit 成功，本工讀生才會動工
	@Transactional(propagation = Propagation.REQUIRES_NEW) // 🚀 建立獨立、乾淨的全新視圖事務
	public void processRoleProjection(TenantEventEnvelope envelope) {
		if (!(envelope.event() instanceof RoleChangedEvent event)) {
			return;
		}

		String tenantId = envelope.tenantId(); // 核心設計：直接從多租戶信封內拔出可靠租戶 ID
		UUID roleId = event.roleId();

		try {
			// 將權限清單打扁成 JSON 文本儲存
			String permissionsJson = objectMapper.writeValueAsString(event.permissions());

			// CQRS Upsert 同步
			RoleView roleView = viewRepository.findByTenantIdAndId(tenantId, roleId)
					.map(existingView -> new RoleView(existingView.getId(), existingView.getTenantId(),
							event.roleName(), existingView.getRoleCode(), // 業務主鍵不可變
							event.isSystemRoot(), permissionsJson))
					.orElseGet(() -> new RoleView(roleId, tenantId, event.roleName(), event.roleCode(),
							event.isSystemRoot(), permissionsJson));

			viewRepository.save(roleView); // 轟入投影表

		} catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
			// 🚀 最終一致性自我修復防線：
			// 若高併發下事件撞車，此處直接溫和記錄日誌。因為 Outbox 表中該事件依舊是 PENDING，
			// 隨後甦醒的定時排程會再次撈起並重新投遞此事件，數據最終必定一致！
			System.err.printf(
					"[Concurrent Update Ignored] Role view sync conflict for code: %s, will be healed by outbox retry.%n",
					event.roleCode());
		} catch (Exception e) {
			System.err.printf("[Projection Failed] Critical error syncing role view: %s, Error: %s%n", event.roleCode(),
					e.getMessage());
		}
	}
}