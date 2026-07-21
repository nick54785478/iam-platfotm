package com.example.demo.iface.event;

import com.example.demo.application.port.IdempotencyHandlerPort;
import com.example.demo.application.service.TenantOnboardingCommandService;
import com.example.demo.iface.dto.payload.TenantProvisionedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>[介面層] 租戶開通整合事件監聽器</h2>
 * <p>
 * 負責攔截來自平台層 (TenantService) 的租戶開通廣播。<br>
 * <b>【架構防護】</b>：<br>
 * 利用 @Transactional 確保「冪等防護紀錄」與「業務資料變更」同生共死。<br>
 * 若業務處理失敗，去重紀錄將一同 Rollback，確保 Kafka 下次重試時能順利放行，達到真正的 Exactly-Once 語意。
 * </p>
 */
/**
 * <h2>[介面層] 租戶開通整合事件監聽器</h2>
 * <p>
 * 負責攔截來自平台層 (TenantService) 的租戶開通廣播。<br>
 * <b>【架構防護】</b>：<br>
 * 利用 @Transactional 確保「冪等防護紀錄」與「業務資料變更」同生共死。<br>
 * 若業務處理失敗，去重紀錄將一同 Rollback，確保 Kafka 下次重試時能順利放行，達到真正的 Exactly-Once 語意。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantProvisionedEventHandler {

    private final ObjectMapper objectMapper;
    private final IdempotencyHandlerPort idempotencyHandler; // 依賴抽象的冪等防護合約
    private final TenantOnboardingCommandService onboardingCommandService;

    private static final String TOPIC_TENANT_PROVISIONED = "topic.platform.tenant";
    private static final String CONSUMER_GROUP = "auth-service-tenant-onboarding";

    @Transactional // 極度重要：保證冪等表寫入與下游業務邏輯在同一個 DB Transaction 中
    @KafkaListener(topics = TOPIC_TENANT_PROVISIONED, groupId = CONSUMER_GROUP)
    public void onTenantProvisioned(ConsumerRecord<String, String> record) {
        try {
            TenantProvisionedPayload payload = objectMapper.readValue(record.value(), TenantProvisionedPayload.class);

            // 1. 執行高併發冪等性去重防禦
            // 底層的 Postgres ON CONFLICT 將自動承擔「行級別阻塞鎖」與「永久去重」的雙重責任
            if (!idempotencyHandler.tryProcess(payload.eventId().toString())) {
                log.debug("[EDA] 忽略已處理的重複租戶開通事件: EventId={}, TenantId={}",
                        payload.eventId(), payload.aggregateId());
                return;
            }

            log.info("[EDA] 接收到新租戶開通事件，準備初始化 Root Admin。TenantId: {}", payload.aggregateId());

            // 2. 跨越六角架構邊界，將流程編排委託給 Application Layer
            // 呼叫 payload.adminEmail() 與 payload.plainPassword()
            onboardingCommandService.initializeTenantRootSecurity(
                    payload.aggregateId(),
                    payload.adminEmail(),
                    payload.plainPassword()
            );

            log.info("[EDA] 租戶 {} 的 Root Admin 初始化流程執行完畢。", payload.aggregateId());

        } catch (Exception e) {
            log.error("[EDA] 處理租戶開通事件時發生嚴重系統異常。Payload: {}", record.value(), e);
            // 拋出 RuntimeException 觸發 Transaction Rollback，
            // 如此一來 idempotencyHandler 的寫入也會被撤銷，等待 Kafka 重新派發
            throw new RuntimeException("Failed to process tenant provisioned event", e);
        }
    }
}