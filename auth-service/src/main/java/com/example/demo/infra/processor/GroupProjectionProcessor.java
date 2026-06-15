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

import com.example.demo.application.domain.group.event.GroupChangedEvent;
import com.example.demo.application.shared.event.TenantEventEnvelope;
import com.example.demo.infra.projection.repository.SpringDataGroupViewRepository;
import com.example.demo.infra.projection.view.GroupView;

/**
 * <h2>[讀取側 - 背景異步] 群組 CQRS 視圖投影處理器 (Group Projection Processor)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為 {@code group_view} 展示櫥窗的非同步裝潢工。 負責異步捕捉群組變更事件，將成員與角色名單摺疊成扁平 CSV
 * 文本儲存，實現讀取側 $O(1)$ 的超凡拉取效能。
 * </p>
 */
@Component
public class GroupProjectionProcessor {

	private final SpringDataGroupViewRepository viewRepository;

	public GroupProjectionProcessor(SpringDataGroupViewRepository viewRepository) {
		this.viewRepository = viewRepository;
	}

	/**
	 * ⚠️ 注意：此方法執行在【獨立背景執行緒】，主寫入線程的 ThreadLocal 隔離在這邊已失效。
	 */
	@Async // 🚀 背景非同步開道，主業務完全不需要等它跑完，前端直接拿 200 OK
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 🚀 只有寫入端主事務完全 Commit 提交成功，本處理器才會動工
	@Transactional(propagation = Propagation.REQUIRES_NEW) // 🚀 建立獨立、乾淨的全新視圖更新事務
	public void processGroupProjection(TenantEventEnvelope envelope) {
		if (!(envelope.event() instanceof GroupChangedEvent event)) {
			return;
		}

		String tenantId = envelope.tenantId(); // 🚀 核心設計：直接從多租戶信封內拔出可靠租戶 ID
		UUID groupId = event.groupId();

		try {
			// 1. 將成員與角色 Set 陣列，直接摺疊成以逗號分隔的扁平 CSV 字串
			String membersCsv = String.join(",", event.memberUserIds());
			String rolesCsv = String.join(",", event.assignedRoleIds());

			// 2. 執行 CQRS Upsert 同步 (存在就蓋寫最新快照，不存在就全新建立)
			GroupView groupView = viewRepository.findByTenantIdAndId(tenantId, groupId)
					.map(existingView -> new GroupView(existingView.getId(), existingView.getTenantId(),
							event.groupName(), existingView.getGroupCode(), // 業務主鍵不可變
							membersCsv, rolesCsv))
					.orElseGet(() -> new GroupView(groupId, tenantId, event.groupName(), event.groupCode(), membersCsv,
							rolesCsv));

			// 3. 直擊投影快照表
			viewRepository.save(groupView);

		} catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
			// 🚀 最終一致性自我修復防線：
			// 若高併發下事件撞車，此處直接溫和記錄日誌。因為 Outbox 表中該事件依舊是 PENDING，
			// 隨後甦醒的定時排程會再次撈起並重新投遞此事件，數據最終必定一致！
			System.err.printf(
					"[Concurrent Update Ignored] Group view sync conflict for code: %s, will be healed by outbox retry.%n",
					event.groupCode());
		} catch (Exception e) {
			System.err.printf("[Projection Failed] Critical error syncing group view: %s, Error: %s%n",
					event.groupCode(), e.getMessage());
		}
	}
}