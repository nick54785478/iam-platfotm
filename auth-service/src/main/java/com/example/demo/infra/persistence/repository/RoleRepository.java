package com.example.demo.infra.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.infra.persistence.entity.role.RoleEntity;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

	// 核心防禦：依據多租戶 + 物理主鍵隔離查詢
	Optional<RoleEntity> findByTenantIdAndId(String tenantId, UUID id);

	// 核心防禦：依據多租戶 + 業務主角 roleCode 隔離查詢
	Optional<RoleEntity> findByTenantIdAndRoleCode(String tenantId, String roleCode);

	List<RoleEntity> findByTenantIdAndIdIn(String currentTenantId, Set<UUID> uuids);

	List<RoleEntity> findByTenantIdAndRoleCodeIn(String currentTenantId, Set<String> roleCodes);
}