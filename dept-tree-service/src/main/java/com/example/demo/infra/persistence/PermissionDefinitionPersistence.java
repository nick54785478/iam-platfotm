package com.example.demo.infra.persistence;


import com.example.demo.application.domain.permission.aggregate.PermissionDefinition;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionCode;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionId;
import com.example.demo.application.domain.shared.vo.TenantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * <h2>[基礎設施層] 權限定義持久化介面 (JPA Repository)</h2>
 * <p>
 * 專責與底層資料庫溝通，支援直接以 VO (值物件) 進行精準查詢。
 * </p>
 */
@Repository
public interface PermissionDefinitionPersistence extends JpaRepository<PermissionDefinition, PermissionId> {

    /**
     * 雙重防護查詢：確保撈出來的 ID 必定屬於該租戶
     */
    Optional<PermissionDefinition> findByTenantIdAndId(TenantId tenantId, PermissionId id);

    /**
     * 透過租戶 ID 與權限代碼精準尋找權限
     */
    Optional<PermissionDefinition> findByTenantIdAndCode(TenantId tenantId, PermissionCode code);

    /**
     * 檢查特定租戶下該權限代碼是否已存在
     */
    boolean existsByTenantIdAndCode(TenantId tenantId, PermissionCode code);
}