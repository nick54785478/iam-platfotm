package com.example.demo.application.domain.role.event;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.example.demo.application.shared.event.DomainEvent;

/**
 * <h2>[領域層 - 事件] 角色與權限狀態已變更事件 (Role Changed Event)</h2>
 * <p>
 * <b>【戰略天職】</b>：本事件同樣為「全量快照變更事件」。
 * 不論是角色更名（rename）、子系統權限點自動上報（assignPermission）、還是權限撤銷，一律內聚發射此事件。 專用於驅動讀取側
 * {@code role_view} 投影表的更新，下游微服務也可以訂閱此事件來即時刷新網關（Gateway）的本地鑑權快取。
 * </p>
 */
public record RoleChangedEvent(UUID eventId, UUID roleId, String roleName, String roleCode, boolean isSystemRoot,
		Set<PermissionInfo> permissions, // 摺疊拉平成扁平化 DTO 結構的權限點清單，方便消費端秒轉成 JSON 文本
		LocalDateTime occurredAt) implements DomainEvent {

	@Override
	public String aggregateType() {
		return "ROLE";
	}

	@Override
	public String aggregateId() {
		return roleId.toString();
	}

	/**
	 * <h3>[領域事件內嵌 Record DTO] 扁平化權限資料結構</h3>
	 * <p>
	 * 完全與資料庫持久化實體解耦，專為網路傳輸與視圖快照設計的唯讀結構。
	 * </p>
	 */
	public record PermissionInfo(String systemCode, String permissionCode, String permissionName) {
	}
}