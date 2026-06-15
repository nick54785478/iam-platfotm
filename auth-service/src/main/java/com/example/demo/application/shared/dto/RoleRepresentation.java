package com.example.demo.application.shared.dto;

import java.util.Set;

public record RoleRepresentation(String id, String roleName, String roleCode, boolean isSystemRoot,
		Set<PermissionDto> permissions) {
	public record PermissionDto(String systemCode, String permissionCode, String permissionName) {
	}
}