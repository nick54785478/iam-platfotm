package com.example.demo.infra.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.infra.persistence.entity.role.RoleDbEntity;

public interface SpringDataRoleRepository extends JpaRepository<RoleDbEntity, UUID> {

	// 🚀 核心防禦：依據多租戶 + 物理主鍵隔離查詢
	Optional<RoleDbEntity> findByTenantIdAndId(String tenantId, UUID id);

	// 🚀 核心防禦：依據多租戶 + 業務主角 roleCode 隔離查詢
	Optional<RoleDbEntity> findByTenantIdAndRoleCode(String tenantId, String roleCode);

	List<RoleDbEntity> findByTenantIdAndIdIn(String currentTenantId, Set<UUID> uuids);

	List<RoleDbEntity> findByTenantIdAndRoleCodeIn(String currentTenantId, Set<String> roleCodes);
}