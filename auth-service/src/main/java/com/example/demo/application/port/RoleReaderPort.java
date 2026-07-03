package com.example.demo.application.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.demo.application.shared.dto.RoleRepresentation;

public interface RoleReaderPort {
	
	Optional<RoleRepresentation> fetchByRoleCode(String tenantId, String roleCode);

	List<RoleRepresentation> fetchAllByTenant(String tenantId);
	
	Optional<RoleRepresentation> fetchByUuid(String tenantId, UUID roleId);

	/**
	 * 批次透過 UUID 獲取角色視圖 (解決群組轉換 N+1)
	 */
	List<RoleRepresentation> fetchByUuids(String tenantId, Set<UUID> roleIds);

	/**
	 * 批次透過 Role Code 獲取角色視圖 (解決最終權限聚合 N+1)
	 */
	List<RoleRepresentation> fetchByRoleCodes(String tenantId, Set<String> roleCodes);

}