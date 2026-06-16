package com.example.demo.iface.event;

import com.example.demo.application.domain.permission.event.PermissionDefinitionCreatedEvent;
import com.example.demo.application.domain.permission.event.PermissionDefinitionUpdatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * <h2>[基礎設施層] 權限事件 Kafka 轉拋攔截器 (Kafka Relay Handler)</h2>
 * <p>
 * <b>【架構定位】</b>：<br>
 * 作為 Outbox Pattern 的最後一哩路。專責監聽由 {@code OutboxEventProcessor}
 * 重新反序列化並派發的領域事件，將其轉換為外部整合事件並推播至 Kafka。
 * 💡 <b>一致性保證：</b> 使用 {@code AFTER_COMMIT}，確保只有在 Outbox 狀態成功被標記為 PROCESSED
 * 並 Commit 至資料庫後，才會真正執行 Kafka 網路傳輸，避免幽靈訊息外洩。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionKafkaRelayHandler {

    private final ObjectMapper objectMapper;
    // 實務上會注入 KafkaTemplate 或對應的 Port
    // private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC_PERMISSION_EVENTS = "sys.permission.events.v1";

    /**
     * 攔截並轉拋：權限建立事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PermissionDefinitionCreatedEvent event) {
        System.out.println("\n========== [Kafka 轉拋器] 攔截到 Outbox 重播事件：PermissionDefinitionCreatedEvent ==========");
        publishToKafka(event.routingKey(), event);
    }

    /**
     * 攔截並轉拋：權限更新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PermissionDefinitionUpdatedEvent event) {
        System.out.println("\n========== [Kafka 轉拋器] 攔截到 Outbox 重播事件：PermissionDefinitionUpdatedEvent ==========");
        publishToKafka(event.routingKey(), event);
    }

    /**
     * 模擬發送至 Kafka 的共用邏輯
     */
    private void publishToKafka(String routingKey, Object eventPayload) {
        try {
            // 1. 序列化為 JSON (實務上這裡可以插入一層 Anti-Corruption Layer，將 DomainEvent 轉為 IntegrationEvent)
            String messageJson = objectMapper.writeValueAsString(eventPayload);

            // 2. 示意 Kafka 發送動作
            System.out.println(">>> [執行 Kafka Send] 準備發送訊息至 Broker");
            System.out.println("    - Target Topic : " + TOPIC_PERMISSION_EVENTS);
            System.out.println("    - Routing Key  : " + routingKey);
            System.out.println("    - Message Body : " + messageJson);

            // 實務上的發送語法：
            // kafkaTemplate.send(TOPIC_PERMISSION_EVENTS, routingKey, messageJson)
            //   .whenComplete((result, ex) -> { ... 處理 ack 或 retry ... });

            System.out.println(">>> [Kafka 發送成功] 訊息已抵達 Broker，等待下游 AuthService 消費\n");

        } catch (JsonProcessingException e) {
            // 由於是 AFTER_COMMIT 階段，拋出 Exception 已經無法 Rollback 資料庫了。
            // 這裡必須搭配 Dead Letter Queue (DLQ) 或 Log 告警，並依賴人工或修復程式重壓 Outbox。
            log.error("Failed to serialize event for Kafka relay. RoutingKey: {}", routingKey, e);
        }
    }
}