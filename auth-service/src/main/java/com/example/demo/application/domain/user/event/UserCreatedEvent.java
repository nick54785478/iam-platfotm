package com.example.demo.application.domain.user.event;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.example.demo.application.shared.event.DomainEvent;

/**
 * <h2>[領域層 - 事件] 使用者賬號已全新建立事件 (User Created Event)</h2>
 * <p>
 * <b>【戰略天職】</b>：當系統全新開闢一個使用者時，由工廠方法內聚觸發。
 * 專用於審計流水追蹤、大數據行為分析、或者發送註冊成功的歡迎信信件（Notification Subsystem）。
 * </p>
 */
public record UserCreatedEvent(UUID eventId, UUID userId, String username, String email, String status,
		Set<UUID> assignedRoleIds, // 建立初期用戶持有的原始角色物理 UUID 集合 (預設為空)
		LocalDateTime occurredAt) implements DomainEvent {

	@Override
	public String aggregateType() {
		return "USER";
	}

	@Override
	public String aggregateId() {
		return userId.toString();
	}
}