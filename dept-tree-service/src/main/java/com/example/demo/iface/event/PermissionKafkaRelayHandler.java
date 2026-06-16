package com.example.demo.iface.event;

import com.example.demo.application.domain.permission.event.PermissionDefinitionCreatedEvent;
import com.example.demo.application.domain.permission.event.PermissionDefinitionUpdatedEvent;
import com.example.demo.application.port.MessagePublisherPort;
import com.example.demo.application.shared.command.outbound.PublishEventCommand;
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
 * 重新反序列化並派發的內部領域事件，將其轉換為外部整合事件並推播至 Kafka。<br>
 * <br>
 * 💡 <b>一致性保證：</b> 使用 {@code AFTER_COMMIT}，確保只有在 Outbox 狀態成功被標記為 PROCESSED
 * 並 Commit 至資料庫後，才會真正執行網路傳輸，徹底根除幽靈訊息（DB Rollback 但 Kafka 已發送）的災難。<br>
 * <b>架構解耦：</b> 透過 {@link MessagePublisherPort} 與 {@link PublishEventCommand} 進行呼叫，
 * 讓轉拋器完全不依賴 Spring Kafka 的特定 API，完美落實六角架構。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionKafkaRelayHandler {

    private final ObjectMapper objectMapper;

    // 注入應用層定義的 Port，取代直接依賴 KafkaTemplate
    private final MessagePublisherPort messagePublisher;

    /**
     * 權限事件專屬 Kafka Topic
     * 命名規範：[類型].[領域邊界].[實體].[事件].[版本]
     */
    private static final String TOPIC_PERMISSION_EVENTS = "topic.dept.permission.events.v1";

    /**
     * 攔截並轉拋：權限建立事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PermissionDefinitionCreatedEvent event) {
        log.trace("Intercepted Outbox Replay Event: [PermissionDefinitionCreatedEvent] for Aggregate [{}]", event.aggregateId());
        publishToRemoteBus(event.routingKey(), event);
    }

    /**
     * 攔截並轉拋：權限更新事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PermissionDefinitionUpdatedEvent event) {
        log.trace("Intercepted Outbox Replay Event: [PermissionDefinitionUpdatedEvent] for Aggregate [{}]", event.aggregateId());
        publishToRemoteBus(event.routingKey(), event);
    }

    /**
     * 執行實質轉譯與發送 (封裝 Port 呼叫)
     *
     * @param routingKey   用作 Kafka Partition Key，確保同一聚合根的事件循序處理
     * @param eventPayload 準備對外發布的事件載體
     */
    private void publishToRemoteBus(String routingKey, Object eventPayload) {
        try {
            // 1. 將領域事件序列化為跨語言通用的 JSON 格式
            // 實務擴充點：若內外部事件結構差異大，可在此處先將 DomainEvent 映射 (Map) 為 IntegrationEvent 再序列化
            String messageJson = objectMapper.writeValueAsString(eventPayload);

            // 2. 封裝成高內聚的指令物件 (PublishEventCommand)
            PublishEventCommand command = PublishEventCommand.builder()
                    .topic(TOPIC_PERMISSION_EVENTS)
                    .routingKey(routingKey)
                    .eventJson(messageJson)
                    .build();

            // 3. 透過 Port 向外部發送，將網路 I/O 的髒活交給 Adapter 處理
            messagePublisher.send(command);

            log.info("Successfully handed over event to Publisher Port. Topic: [{}], RoutingKey: [{}]",
                    TOPIC_PERMISSION_EVENTS, routingKey);

        } catch (JsonProcessingException e) {
            // 基礎設施防呆底線：
            // 由於此時已處於 AFTER_COMMIT 階段，資料庫已經落盤，拋出 Exception 無法觸發 Rollback。
            // 遇到序列化等致命錯誤時，必須依賴日誌告警，並由人工或排程修復 Outbox 內的資料狀態。
            log.error("Failed to serialize event payload for remote broadcasting. RoutingKey: [{}]", routingKey, e);
        }
    }
}