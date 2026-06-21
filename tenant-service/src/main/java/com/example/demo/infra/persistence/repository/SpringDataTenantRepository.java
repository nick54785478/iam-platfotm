package com.example.demo.infra.persistence.repository;

import com.example.demo.infra.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * <h2>[基礎設施層] 租戶 Spring Data JPA 儲存庫</h2>
 */
@Repository
public interface SpringDataTenantRepository extends JpaRepository<TenantEntity, String> {
    // 由於 TenantService 屬於全域平台層，查詢時直接直擊 PK，無需額外的 tenant_id 欄位隔離
}