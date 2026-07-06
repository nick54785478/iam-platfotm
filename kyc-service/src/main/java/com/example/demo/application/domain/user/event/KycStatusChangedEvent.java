package com.example.demo.application.domain.user.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * <b>[領域事件] KYC 審核狀態已變更</b>
 * <p>
 * 安全廣播事件：通知 Auth/Payment 等周邊服務用戶實名狀態，絕不包含明文個資。
 * </p>
 */
public record KycStatusChangedEvent(UUID eventId, String aggregateId, LocalDateTime occurredAt,

		String tenantId, String oldStatus, String newStatus, String rejectReason, Long version) implements DomainEvent {

	public KycStatusChangedEvent(String tenantId, String aggregateId, String oldStatus, String newStatus,
			String rejectReason, Long version) {
		this(UUID.randomUUID(), aggregateId, LocalDateTime.now(), tenantId, oldStatus, newStatus, rejectReason,
				version);
	}

	@Override
	public String aggregateType() {
		return "UserIdentity";
	}
}