package com.example.demo.application.shared.event;



/**
 * <h2>[應用/基礎設施層 - 信封] 多租戶全局領域事件信封 (Tenant Event Envelope)</h2>
 * <p>
 * <b>【硬核設計美感 - 領域無污染原則】</b>：<br>
 * 這是整套高階六角形架構中最讚的設計之一！ 依照 DDD 規範，領域事件（如
 * {@code UserChangedEvent}）應該保持最純粹的業務語意，不該強行塞入框架級的 {@code tenantId} 欄位。 *
 * <p>
 * 因此，當持久化層（WriterAdapter）從 {@code TenantContext} 拔出當前租戶 ID 並準備對外發射事件時，
 * 會在基礎設施外圈親手打造這個<b>「多租戶信封」</b>，將純領域事件包裹在內。 背景的 Outbox 監聽器與 Exporter
 * 排程拆開信封後，便能同時收穫『穩定的租戶標籤』與『純潔的領域變更』，完美兼顧技術規格與業務純潔度。
 * </p>
 */
public record TenantEventEnvelope(String tenantId, // 從上下文中拆解出來、安全且不容被篡改的 SaaS 租戶識別碼
		DomainEvent event // 被安全保護在內圈的純領域事件本體
) {
}