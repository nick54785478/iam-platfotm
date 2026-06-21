package com.example.demo.application.shared.event;

import java.time.Instant;
import java.util.UUID;

/**
 * <h2>[共享內核] 外部整合事件頂層契約 (Integration / Outbound Event)</h2>
 * <p>
 * <b>【設計邊界】</b>：<br>
 * 本介面專供跨微服務通訊（如透過 Outbox 寫入 Kafka）的事件載體使用。<br>
 * 徹底剔除了 {@code aggregateId} 等內部領域模型特徵，只保留外部消費者進行
 * 路由分發、去重防禦與時序校驗所需的最小元數據。
 * </p>
 */
public interface OutboundEvent {

    /**
     * @return 宇宙唯一事件識別碼 (用於下游消費端進行 Idempotent 去重)
     */
    UUID eventId();

    /**
     * @return 事件類型 (例如 "TenantProvisioned"，供下游反序列化或多態路由使用)
     */
    String eventType();

    /**
     * @return 觸發此事件的租戶 ID (實現多租戶資料隔離的絕對依據)
     */
    String tenantId();

    /**
     * @return 系統正式發出此事件的時間戳記 (用於時光機排序或捨棄過期亂序事件)
     */
    Instant occurredAt();
}
