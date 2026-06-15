package com.example.demo.iface.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.domain.group.event.GroupChangedEvent;
import com.example.demo.application.domain.role.event.RoleChangedEvent;
import com.example.demo.application.domain.user.event.UserChangedEvent;
import com.example.demo.application.shared.event.DomainEvent;
import com.example.demo.application.shared.event.TenantEventEnvelope;
import com.example.demo.infra.outbox.entity.OutboxEventDbEntity;
import com.example.demo.infra.outbox.repository.SpringDataOutboxRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * <h2>[寫入側 - 事務內] 全局領域事件 Outbox 落地監聽器</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為寫入端主業務事務內的安全警報器。負責在主業務（如 createUser）即將 Commit 提交的最後一刻， 攔截發出的領域事件，將其同步刻進
 * {@code outbox_events} 表中，扮演一條龍事務的守門員。
 * </p>
 */
@Component
public class DomainEventOutboxListener {

	private final SpringDataOutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;

	public DomainEventOutboxListener(SpringDataOutboxRepository outboxRepository, ObjectMapper objectMapper) {
		this.outboxRepository = outboxRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * 攔截包裹了多租戶信封的領域事件。 ⚠️ 注意：此處執行在【主業務請求執行緒】，一旦本方法序列化或資料庫 save 噴錯，
	 * 前面的使用者、角色變更將會「連帶強制 Rollback」，確保兩者高度的 ACID 一致性。
	 */
	@EventListener
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT) // 🚀 核心防線：主事務提交前的命運共同體
	public void onTenantDomainEvent(TenantEventEnvelope envelope) {
		try {
			DomainEvent domainEvent = envelope.event();

			// 利用 Pattern Matching，優雅收容需要被導流非同步投影的核心狀態事件
			if (domainEvent instanceof UserChangedEvent || domainEvent instanceof RoleChangedEvent
					|| domainEvent instanceof GroupChangedEvent) {

				// 1. 將強型態領域事件壓扁為 JSON Payload
				String jsonPayload = objectMapper.writeValueAsString(domainEvent);

				// 2. 組裝泛型多租戶的 Outbox 實體，狀態預設為 "PENDING"
				OutboxEventDbEntity outbox = new OutboxEventDbEntity(domainEvent.eventId(), // 宇宙唯一事件碼
						envelope.tenantId(), // 從信封無痛拆出的多租戶隔離標籤
						domainEvent.aggregateType(), // 聚合根種類，如 "USER" 或 "ROLE"
						domainEvent.aggregateId(), // 聚合根唯一 ID
						domainEvent.getClass().getSimpleName(), // 事件類別名稱，如 "UserChangedEvent"
						jsonPayload // JSON 資料
				);

				// 3. 儲存進 Outbox 實體表，等待排程馬達（Exporter）輪詢發射
				outboxRepository.save(outbox);
			}

		} catch (Exception e) {
			// 硬核阻斷：寫入保險箱失敗就必須倒地、強迫主線程 Rollback，絕不允許漏掉任何發變更
			throw new RuntimeException("Failed to persistent domain event to outbox via tenant envelope", e);
		}
	}
}