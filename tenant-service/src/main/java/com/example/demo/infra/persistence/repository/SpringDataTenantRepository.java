package com.example.demo.infra.persistence.repository;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;
import com.example.demo.application.shared.dto.TenantSummarySearchedView;
import com.example.demo.infra.persistence.entity.TenantEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * <h2>[基礎設施層] 租戶 Spring Data JPA 儲存庫</h2>
 */
@Repository
public interface SpringDataTenantRepository extends JpaRepository<TenantEntity, String> {
    // 由於 TenantService 屬於全域平台層，查詢時直接直擊 PK，無需額外的 tenant_id 欄位隔離

    /**
     * <b>極速直擊查詢：根據 ID 進行單一投影</b>
     */
    <T> Optional<T> findByTenantId(String tenantId, Class<T> type);

    /**
     * <b>萬能高併發分頁動態過期過濾查詢</b>
     * <p>
     * 支援動態過濾公司名稱、方案與狀態，並直接在資料庫內完成動態分頁與 DTO 投影。
     * </p>
     */
    @Query("""
        SELECT new com.example.demo.application.shared.dto.TenantSummarySearchedView(
            t.tenantId, t.companyName, t.planType, t.status
        )
        FROM TenantEntity t
        WHERE (:companyName IS NULL OR t.companyName LIKE %:companyName%)
          AND (:planType IS NULL OR t.planType = :planType)
          AND (:status IS NULL OR t.status = :status)
        """)
    Page<TenantSummarySearchedView> findTenantsByFilters(
            @Param("companyName") String companyName,
            @Param("planType") PlanType planType,
            @Param("status") TenantStatus status,
            Pageable pageable
    );
}