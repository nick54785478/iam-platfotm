package com.example.demo.application.shared.event;


import java.time.Instant;
import java.util.UUID;

/**
 * <h2>[外部整合事件] SaaS 租戶開通完畢</h2>
 * <p>
 * <b>【發布時機】</b>：當 TenantService 成功建立新租戶，並準備通知全局系統進行初始化時發布。<br>
 * <b>【消費者】</b>：<br>
 * - AuthService: 攔截此事件以建立 Root Admin 初始帳號與頂級權限。<br>
 * - DeptTreeService: 攔截此事件以建立該企業的總部根節點。
 * </p>
 */
public record TenantProvisionedEvent(

        // -------------------------------------------------------------------
        // 🛡️ 實作 OutboundEvent 契約的基礎元數據
        // -------------------------------------------------------------------
        UUID eventId,
        String eventType,
        String tenantId,
        Instant occurredAt,

        // -------------------------------------------------------------------
        // 📦 專屬業務負載 (Payload) - 供下游系統初始化的核心參數
        // -------------------------------------------------------------------
        String companyName,
        String rootAdminEmail,
        String rootAdminTempPassword,
        String planType

) implements OutboundEvent {

    /**
     * 🏭 靜態工廠方法：提供給 Application Service 快速建構合法的對外事件
     */
    public static TenantProvisionedEvent create(
            String tenantId,
            String companyName,
            String rootAdminEmail,
            String rootAdminTempPassword,
            String planType) {

        return new TenantProvisionedEvent(
                UUID.randomUUID(),           // 自動生成唯一防重 ID
                "TenantProvisioned",         // 寫死事件類型，防呆
                tenantId,
                Instant.now(),               // 壓入當下時間戳
                companyName,
                rootAdminEmail,
                rootAdminTempPassword,
                planType
        );
    }
}