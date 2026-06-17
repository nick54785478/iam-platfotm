package com.example.demo.infra.projection.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.projection.view.RoleView;

@Repository
public interface RoleViewRepository extends JpaRepository<RoleView, UUID> {

	/**
	 * 🚀 供 RoleReaderAdapter 呼叫：完全對齊以業務鍵 roleCode 為主角的 API 規格 底層會直接轟擊 (tenant_id,
	 * role_code) 的複合唯一索引，實現 O(1) 級別的讀取效能
	 */
	Optional<RoleView> findByTenantIdAndRoleCode(String tenantId, String roleCode);

	/**
	 * 🚀 供 RoleProjectionProcessor 同步時呼叫： 因為 Outbox 事件 (RoleChangedEvent) 肚子裡帶的是物理
	 * UUID，透過此方法確認是該更新（Map）還是新增（orElseGet）
	 */
	Optional<RoleView> findByTenantIdAndId(String tenantId, UUID id);

	/**
	 * 查詢特定租戶下的所有角色視圖清單 (Read List)
	 */
	List<RoleView> findByTenantId(String tenantId);
}