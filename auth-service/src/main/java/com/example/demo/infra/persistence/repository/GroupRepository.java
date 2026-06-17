package com.example.demo.infra.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.entity.group.GroupEntity;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, UUID> {

	/**
	 * 🚀 核心防禦：依據當前隔離租戶標籤 + 物理 UUID 主鍵查詢群組
	 */
	Optional<GroupEntity> findByTenantIdAndId(String tenantId, UUID id);

	/**
	 * 🚀 核心防禦：依據當前隔離租戶標籤 + 業務主角 groupCode 查詢群組
	 */
	Optional<GroupEntity> findByTenantIdAndGroupCode(String tenantId, String groupCode);

	
	List<GroupEntity> findByTenantIdAndMemberUserIdsContaining(String currentTenantId, UUID value);
}