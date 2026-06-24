package com.example.demo.application.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * <h2>[應用層] API 規則異動整合事件</h2>
 * <p>
 * <b>【架構定位】</b>：<br>
 * 實作 {@link OutboundEvent}，代表此事件不僅用於觸發本地 Redis 快取清理，<br>
 * 未來亦可無縫接入 Outbox Pattern，透過 Kafka 廣播通知 API Gateway 進行全域路由快取刷新。
 * </p>
 */
public record ApiRuleChangedEvent(
        UUID eventId,
        String eventType,
        String tenantId,
        Instant occurredAt,

        // --- 核心 Payload 區塊 ---
        String action, // 操作類型：CREATE, UPDATE, TOGGLE
        Long ruleId    // 異動的規則 ID
) implements OutboundEvent {

    /**
     * 靜態工廠方法：封裝基礎設施層級的元數據，讓 Application Service 呼叫時保持乾淨。
     */
    public static ApiRuleChangedEvent of(String tenantId, String action, Long ruleId) {
        return new ApiRuleChangedEvent(
                UUID.randomUUID(),
                "ApiResourceRuleChanged",
                // 若為全域平台規則，tenantId 可能為空，給予預設系統標籤
                (tenantId != null && !tenantId.isBlank()) ? tenantId : "PLATFORM_SYSTEM",
                Instant.now(),
                action,
                ruleId
        );
    }
}