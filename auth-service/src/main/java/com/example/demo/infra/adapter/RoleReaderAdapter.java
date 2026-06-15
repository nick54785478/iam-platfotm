package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.demo.application.port.RoleReaderPort;
import com.example.demo.application.shared.dto.RoleRepresentation;
import com.example.demo.application.shared.dto.RoleRepresentation.PermissionDto;
import com.example.demo.infra.projection.repository.SpringDataRoleViewRepository;
import com.example.demo.infra.projection.view.RoleView;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class RoleReaderAdapter implements RoleReaderPort {

	private final SpringDataRoleViewRepository viewRepository;
	private final ObjectMapper objectMapper;

	public RoleReaderAdapter(SpringDataRoleViewRepository viewRepository, ObjectMapper objectMapper) {
		this.viewRepository = viewRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	public Optional<RoleRepresentation> fetchByRoleCode(String tenantId, String roleCode) {
		// 直擊我們在 role_view 上建立的複合索引方法
		return viewRepository.findByTenantIdAndRoleCode(tenantId, roleCode).map(this::toRepresentation);
	}

	@Override
	public List<RoleRepresentation> fetchAllByTenant(String tenantId) {
		return viewRepository.findByTenantId(tenantId).stream().map(this::toRepresentation).toList();
	}

	@Override
	public Optional<RoleRepresentation> fetchByUuid(String tenantId, UUID roleId) {
		// 🚀 實作：利用租戶與物理主鍵，精準打擊 role_view
		return viewRepository.findByTenantIdAndId(tenantId, roleId).map(this::toRepresentation);
	}

	private RoleRepresentation toRepresentation(RoleView view) {
		try {
			// 將扁平的 JSON 字串秒轉為 DTO 集合，避開複雜一對多 JOIN 帶來的效能損耗
			Set<PermissionDto> permissions = objectMapper.readValue(view.getPermissionsJson(),
					new TypeReference<Set<PermissionDto>>() {
					});

			return new RoleRepresentation(view.getId().toString(), view.getRoleName(), view.getRoleCode(),
					view.isSystemRoot(), permissions);
		} catch (Exception e) {
			throw new RuntimeException("Failed to deserialize view permissions JSON", e);
		}
	}
}