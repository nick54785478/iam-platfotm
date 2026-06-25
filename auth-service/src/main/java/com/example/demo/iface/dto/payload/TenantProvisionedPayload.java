package com.example.demo.iface.dto.payload;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <h2>[介面層] 租戶開通整合事件專用 Payload</h2>
 * <p>
 * 欄位名稱已嚴格對齊 Kafka JSON Payload，確保 Jackson 反序列化時能精準映射。
 * </p>
 */
public record TenantProvisionedPayload(
        UUID eventId,
        String aggregateId,
        String companyName,
        String adminEmail,     // 對齊 JSON 的 adminEmail
        String plainPassword,  // 對齊 JSON 的 plainPassword
        String planType,
        LocalDateTime occurredAt // 補齊時間戳記，避免 Jackson 解析未知屬性報錯
) {}