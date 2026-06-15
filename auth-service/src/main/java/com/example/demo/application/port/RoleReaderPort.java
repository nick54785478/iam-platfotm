package com.example.demo.application.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.demo.application.shared.dto.RoleRepresentation;

public interface RoleReaderPort {
	
	Optional<RoleRepresentation> fetchByRoleCode(String tenantId, String roleCode);

	List<RoleRepresentation> fetchAllByTenant(String tenantId);
	
	Optional<RoleRepresentation> fetchByUuid(String tenantId, UUID roleId);
}