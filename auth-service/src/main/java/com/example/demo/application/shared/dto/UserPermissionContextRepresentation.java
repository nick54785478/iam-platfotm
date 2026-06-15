package com.example.demo.application.shared.dto;

import java.util.Set;

/**
 * <h2>使用者全量權限與角色上下文 Representation DTO</h2>
 */
public record UserPermissionContextRepresentation(String username, String email, String status,
		Set<String> personalRoles, // 🚀 個人直屬持有角色代碼集合 (例如: ["ROLE_ADMIN"])
		Set<GroupRoleInfo> groupRoles, // 🚀 繼承自群組的角色資訊集合 (包含群組代碼與角色代碼)
		Set<PermissionDto> permissions // 🚀 聯集去重後的最終全量權限點集合
) {
	/** 內嵌 DTO：群組與角色的繼承關係 */
	public record GroupRoleInfo(String groupCode, String groupName, Set<String> roleCodes) {
	}

	/** 內嵌 DTO：扁平化權限點 */
	public record PermissionDto(String systemCode, String permissionCode, String permissionName) {
	}
}