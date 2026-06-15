package com.example.demo.application.domain.user.event;


import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.application.shared.event.DomainEvent;


/**
 * <h2>[領域層 - 事件] 使用者密碼已安全變更事件 (User Password Changed Event)</h2>
 * <p>
 * <b>【戰略天職】</b>：密碼屬於高度敏感隱私。當用戶密碼被修改時，內聚引爆此事件。 下游安全稽核模組（Security
 * Audit）可以監聽此事件，強制讓該用戶在其他裝置的 JWT Token 當場失效，或發送「密碼變更安全警報」簡訊。
 * </p>
 */
public record UserPasswordChangedEvent(UUID eventId, UUID userId, LocalDateTime occurredAt) implements DomainEvent {

	@Override
	public String aggregateType() {
		return "USER";
	}

	@Override
	public String aggregateId() {
		return userId.toString();
	}
}