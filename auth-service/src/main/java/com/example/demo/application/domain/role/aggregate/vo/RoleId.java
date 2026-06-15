package com.example.demo.application.domain.role.aggregate.vo;


import java.util.UUID;

/**
 * <h2>[領域層 - 值物件] 角色強型態識別碼 (Role ID Value Object)</h2>
 * <p>
 * 透過強型態包裝原始 UUID，防止與 UserId 在 Service 層中因誤傳而產生技術位移。
 * </p>
 */
public record RoleId(UUID value) {

	public RoleId{if(value==null){throw new IllegalArgumentException("Role ID cannot be null");}}

	/**
	 * 🚀 業務工廠：生成一個全新的角色物理主鍵
	 */
	public static RoleId generate() {
		return new RoleId(UUID.randomUUID());
	}

	/**
	 * 從標準 UUID 字串還原強型態角色主鍵
	 */
	public static RoleId fromString(String id) {
		return new RoleId(UUID.fromString(id));
	}
}