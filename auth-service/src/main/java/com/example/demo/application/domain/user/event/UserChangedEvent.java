package com.example.demo.application.domain.user.event;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * <h2>[領域層 - 事件] 使用者狀態已完整變更事件 (User Changed Event)</h2>
 * <p>
 * <b>【命名規範】</b>：完美遵循 {@code N + Ved + Event} 規格。<br>
 * <b>【戰略天職】</b>：本事件為<b>「全量狀態快照事件」</b>。 專門用於驅動 CQRS 讀取側的 {@code user_view}
 * 投影表更新。它肚子裡裝載了使用者最新的完全體狀態，
 * 使得背景的裝潢工（UserProjectionProcessor）不需要回頭去查寫入表，即可一發完成視圖重寫。
 * </p>
 */
public record UserChangedEvent(UUID eventId, // 實作契約：事件唯一碼，用於冪等防禦
		UUID userId, // 發生變更的使用者 ID
		String username, // 當前最新的使用者名稱 (業務不變鍵)
		String email, // 當前最新的電子郵件
		String status, // 當前最新的賬號生命週期狀態 (ACTIVE, LOCKED, DEACTIVATED)
		Set<String> roles, // 🚀 規格對齊：由應用層轉譯還原後的「人類可讀角色代碼集合」（如 ["ADMIN", "SALES"]）
		LocalDateTime occurredAt // 實作契約：事件發生時間
) implements DomainEvent {

	@Override
	public String aggregateType() {
		return "USER"; // 內聚定義：此事件歸屬於 USER 聚合根宇宙
	}

	@Override
	public String aggregateId() {
		return userId.toString(); // 內聚定義：綁定的實體標識字串
	}
}