package com.example.demo.application.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.user.aggregate.UserIdentity;
import com.example.demo.application.domain.user.event.KycStatusChangedEvent;
import com.example.demo.application.port.KycCommandRepositoryPort;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.outbox.entity.OutboxEventEntity;
import com.example.demo.infra.outbox.repository.OutboxRepository;
import com.example.demo.infra.projection.repository.KycBackofficeViewRepository;
import com.example.demo.infra.projection.view.KycBackofficeView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <h2>[基礎設施層] KYC 領域事件總機 (Outbox 寫入 + 本地 CQRS 投影)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 作為 KYC 狀態變更的唯一攔截點。在 Transaction 準備 Commit 的前一刻：<br>
 * 1. 執行狀態充填 (Enrichment)，同步更新本地高讀取效能的視圖表。<br>
 * 2. 將輕量級事件寫入 Outbox，等待排程器拋轉至 Kafka。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KycDomainEventDispatcher {

	private final OutboxRepository outboxRepository;
	private final KycBackofficeViewRepository viewRepository;
	private final KycCommandRepositoryPort commandRepository; // 用來回查明文 PII
	private final ObjectMapper objectMapper;

	/**
	 * <b>【極限天坑防禦：BEFORE_COMMIT】</b> 本地視圖更新與 Outbox 寫入綁定於同一個 Transaction，同生共死。
	 */
	@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
	public void onDomainEvent(TenantEventEnvelope envelope) {
		DomainEvent event = envelope.event();
		String tenantId = envelope.tenantId();

		// 只攔截 KYC 狀態變更事件
		if (!(event instanceof KycStatusChangedEvent kycEvent)) {
			return;
		}

		log.debug("[CQRS] 攔截到 KYC 狀態變更 (ID: {}), 準備執行雙軌派發", kycEvent.aggregateId());

		try {
			// ==========================================
			// 軌道一：本地 CQRS 投影 (狀態充填 Enrichment)
			// ==========================================
			updateLocalQueryView(tenantId, kycEvent);

			// ==========================================
			// 軌道二：Outbox 發件匣寫入 (Kafka 廣播準備)
			// ==========================================
			String payload = objectMapper.writeValueAsString(kycEvent);
			String eventType = kycEvent.getClass().getSimpleName();

			// 使用統一的 Outbox 充血建構式 (status 預設為 PENDING)
			OutboxEventEntity outboxEntity = new OutboxEventEntity(event.eventId(), // 宇宙唯一事件碼
					tenantId, event.aggregateType(), // "UserIdentity"
					event.aggregateId(), eventType, payload);

			outboxRepository.save(outboxEntity);
			log.debug("[Outbox] 成功封存 KYC 領域事件準備拋轉。EventId: {}", event.eventId());

		} catch (JsonProcessingException e) {
			log.error("[Dispatcher] KYC 領域事件 JSON 序列化失敗，觸發安全 Rollback！EventId: {}", event.eventId(), e);
			throw new IllegalStateException("Failed to serialize KYC domain event to outbox payload", e);
		} catch (Exception e) {
			log.error("[Dispatcher] KYC 領域事件派發失敗，觸發安全 Rollback！EventId: {}", event.eventId(), e);
			throw new IllegalStateException("Failed to dispatch KYC events.", e);
		}
	}

	/**
	 * <b>執行本地視圖同步與資料充填</b>
	 */
	private void updateLocalQueryView(String tenantId, KycStatusChangedEvent event) {
		// 1. 回頭找 Command 聚合根要完整的明文資料
		UserIdentity aggregate = commandRepository.findById(event.aggregateId())
				.orElseThrow(() -> new IllegalStateException("無法在 CommandDB 找到對應的聚合根：" + event.aggregateId()));

		String fullName = (aggregate.getRealName() != null) ? aggregate.getRealName().fullName() : null;
		String maskedId = (aggregate.getNationalId() != null) ? aggregate.getNationalId().getMaskedNumber() : null;

		// 🚀 核心修復：處理新實體 Version 為 null 的時間差問題
		// 如果 event.version() 是 null，代表這是一個全新的聚合根，Hibernate 尚未賦予版本號，我們預設為 0L
		Long effectiveVersion = (event.version() != null) ? event.version() : 0L;

		// 2. 執行 Upsert
		viewRepository.findByTenantIdAndId(tenantId, event.aggregateId())
				.ifPresentOrElse(
						existingView -> {
							// 🚀 使用 effectiveVersion
							boolean updated = existingView.syncDetails(
									fullName, maskedId, event.newStatus(),
									event.rejectReason(), effectiveVersion
							);
							if (updated) {
								viewRepository.save(existingView);
							}
						},
						() -> {
							// 🚀 使用 effectiveVersion
							KycBackofficeView newView = KycBackofficeView.createNew(
									event.aggregateId(), tenantId, fullName, maskedId,
									event.newStatus(), event.rejectReason(), effectiveVersion
							);
							viewRepository.save(newView);
						}
				);
	}
}