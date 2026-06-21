package com.example.demo.application.domain.tenant.event;



import com.example.demo.application.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <h2>[領域事件] SaaS 租戶開通完畢 (內部寫入側)</h2>
 * <p>
 * <b>【發布時機】</b>：由 Tenant 聚合根的 {@code provisionNew()} 業務工廠方法觸發。<br>
 * <b>【核心職責】</b>：作為 TenantService 內部的 SSOT (單一真實來源)，
 * 負責將新租戶的合約與初始超級管理員密碼等核心機密，安全地傳遞給 Outbox 基礎設施。
 * </p>
 */
public record TenantProvisionedDomainEvent(

        // ── 實作 DomainEvent 介面的基礎元數據 ──
        UUID eventId,
        String aggregateId,      // 即為 TenantId
        // ── 租戶開通專屬的業務負載 (Payload) ──
        String companyName,
        String adminEmail,
        String plainPassword,    // 初始明碼密碼 (將由 Outbox 加密後或直接封裝拋給 Auth 服務)
        String planType,
        LocalDateTime occurredAt

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