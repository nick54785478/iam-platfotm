package com.example.demo.iface.event;


import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.outbox.entity.OutboxEventDbEntity;
import com.example.demo.infra.outbox.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * <h2>[基礎設施層] 領域事件 Outbox 攔截與持久化監聽器</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 作為分散式系統的「黃金保險箱守門員」。它會攔截由 Adapter 拋出的 {@link TenantEventEnvelope}，
 * 將其序列化為 JSON，並轉化為 {@link OutboxEventDbEntity} 寫入資料庫。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DomainEventOutboxListener {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * <b>【極限天坑防禦：TransactionPhase.BEFORE_COMMIT】</b>
     * <p>
     * 絕對不能用普通的 @EventListener！<br>
     * 必須設定為 BEFORE_COMMIT，代表這個監聽器會在「業務方法 (如 TenantOnboarding) 準備 Commit，但尚未真正 Commit」的微秒間隙觸發。
     * 這樣 Outbox 的 Insert 語句才會被掛載到同一個 Database Transaction 中。
     * 若序列化失敗或 Insert 報錯，整個業務（包含建立 Tenant / User）會一起 Rollback，徹底杜絕「資料庫有資料，但 Kafka 沒發出去」的幽靈狀態！
     * </p>
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onDomainEvent(TenantEventEnvelope envelope) {
        DomainEvent event = envelope.event();
        String tenantId = envelope.tenantId();

        try {
            // 1. 將充血模型的強型別事件，降維打擊成扁平的 JSON Payload
            String payload = objectMapper.writeValueAsString(event);

            // 2. 萃取事件的類別名稱作為 EventType (例如 "TenantProvisionedDomainEvent")
            String eventType = event.getClass().getSimpleName();

            // 3. 建立 Outbox 實體
            OutboxEventDbEntity outboxEntity = new OutboxEventDbEntity(
                    event.eventId(),
                    tenantId,
                    event.aggregateType(),
                    event.aggregateId(),
                    eventType,
                    payload
            );

            // 4. 同步寫入資料庫 (依附於當前外層的 Transaction)
            outboxRepository.save(outboxEntity);

            log.debug("[Outbox] 成功攔截並封存領域事件。EventId: {}, Type: {}", event.eventId(), eventType);

        } catch (JsonProcessingException e) {
            // 🚨 致命異常：如果 JSON 序列化失敗，必須拋出 RuntimeException 來強制中斷並 Rollback 當前 Transaction！
            log.error("[Outbox] 領域事件序列化 JSON 失敗，觸發強制 Rollback！EventId: {}", event.eventId(), e);
            throw new IllegalStateException("Failed to serialize domain event to outbox payload", e);
        }
    }
}