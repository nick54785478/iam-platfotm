package com.example.demo.application.domain.group.event;


import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * <h2>[領域層 - 事件] 群組狀態與關係已完整變更事件 (Group Changed Event)</h2>
 * <p>
 * <b>【戰略天職】</b>：本事件為「全量狀態快照事件」。 不論是群組更名、加入成員、移除成員或是指派角色，一律發射此事件。
 * 肚子裡裝載了該群組最新的完全體關係快照，專用於驅動讀取側的 {@code group_view} 投影表更新。
 * </p>
 */
public record GroupChangedEvent(UUID eventId, // 實作契約：事件唯一識別碼（用於分散式去重）
		UUID groupId, // 發生變更的群組 ID
		String groupName, // 當前最新的群組名稱
		String groupCode, // 群組唯一業務編碼 (不可變)
		Set<String> memberUserIds, // 🚀 摺疊拉平後的物理用戶 ID 字串集合
		Set<String> assignedRoleIds, // 🚀 摺疊拉平後的物理角色 ID 字串集合
		LocalDateTime occurredAt) implements DomainEvent {

	@Override
	public String aggregateType() {
		return "GROUP";
	}

	@Override
	public String aggregateId() {
		return groupId.toString();
	}
}