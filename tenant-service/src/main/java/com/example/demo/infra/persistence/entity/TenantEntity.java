package com.example.demo.infra.persistence.entity;

import com.example.demo.application.domain.tenant.aggregate.Tenant;
import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantId;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * <h2>[基礎設施層] 租戶關係型資料庫實體 (Tenant JPA Entity)</h2>
 */
@Getter
@Entity
@Table(name = "tenants")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantEntity {

    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;

    @Column(name = "company_name", nullable = false, length = 128)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 32)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private TenantStatus status;

    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    // ── 雙向轉換邏輯 (Mappers) ──

    /**
     * 工廠方法：將純潔的領域聚合根轉換為 JPA 物理實體
     */
    public static TenantEntity fromDomain(Tenant domain) {
        TenantEntity entity = new TenantEntity();
        entity.tenantId = domain.getId().value();
        entity.companyName = domain.getCompanyName();
        entity.planType = domain.getPlanType();
        entity.status = domain.getStatus();
        entity.expiryDate = domain.getExpiryDate();
        return entity;
    }

    /**
     * 狀態更新：將領域聚合根的最新變更覆蓋至既有的 DB 實體
     */
    public void updateFromDomain(Tenant domain) {
        this.companyName = domain.getCompanyName();
        this.planType = domain.getPlanType();
        this.status = domain.getStatus();
        this.expiryDate = domain.getExpiryDate();
    }

    /**
     * 數據還原 (Rehydration)：將資料庫欄位滿血復活為具備業務行為的充血聚合根
     */
    public Tenant toDomain() {
        return new Tenant(
                new TenantId(this.tenantId),
                this.companyName,
                this.planType,
                this.status,
                this.expiryDate
        );
    }
}