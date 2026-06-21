package com.example.demo.application.domain.tenant.event;


import com.example.demo.application.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <h2>[領域事件] SaaS 租戶遭停權處分 (內部寫入側)</h2>
 * <p>
 * <b>【發布時機】</b>：由 Tenant 聚合根的 {@code suspend()} 管理業務方法觸發。<br>
 * <b>【核心職責】</b>：記錄租戶被停權的當下軌跡與原因。
 * 未來可藉由 Outbox 轉發至 Auth 服務，用於強制註銷該租戶下所有活躍的 JWT Session，
 * 或是轉發給 Gateway 進行直接的流量黑名單阻斷。
 * </p>
 */
public record TenantSuspendedDomainEvent(

        // ── 實作 DomainEvent 介面的基礎元數據 ──
        UUID eventId,
        String aggregateId,      // 即為 TenantId
        LocalDateTime occurredAt,

        // ── 停權專屬的業務負載 (Payload) ──
        String reason            // 停權原因 (如："逾期未繳費超過 30 天", "違反 AUP 條款")

) implements DomainEvent {

    /**
     * <b>【領域不變性】</b>
     * 寫死該事件所屬的聚合根類型，供外圈 Outbox 進行 Topic 分流。
     */
    @Override
    public String aggregateType() {
        return "TENANT";
    }
}