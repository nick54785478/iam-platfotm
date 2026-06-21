package com.example.demo.application.domain.tenant.aggregate;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantId;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;
import com.example.demo.application.domain.tenant.event.TenantProvisionedDomainEvent;
import com.example.demo.application.domain.tenant.event.TenantSuspendedDomainEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * <h2>[領域層 - 聚合根] SaaS 租戶 (Tenant)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 掌管企業租戶在 SaaS 平台上的生命週期（開通、升級、停權、續約）。
 * 嚴格封裝狀態流轉，確保合約與計費方案的不變性（Invariants）。
 * </p>
 */
public class Tenant {

    private final TenantId id;
    private String companyName;
    private PlanType planType;
    private TenantStatus status;
    private Instant expiryDate;

    // 領域事件收集器
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    /**
     * <b>【重建用建構式】</b> 供 Repository 還原資料庫狀態用
     */
    public Tenant(TenantId id, String companyName, PlanType planType, TenantStatus status, Instant expiryDate) {
        this.id = id;
        this.companyName = companyName;
        this.planType = planType;
        this.status = status;
        this.expiryDate = expiryDate;
    }

    /**
     * <b>【業務工廠方法】新租戶入駐 (Onboarding)</b>
     * <p>
     * 執行商業規則校驗，並觸發系統級別的 TenantProvisioned 事件，
     * 以便通知 Auth 與 Dept 子系統進行初始化。
     * </p>
     *
     * @param companyName 公司名稱
     * @param planType    選購方案
     * @param adminEmail  指定的初始超級管理員信箱
     * @param plainPassword 初始明碼密碼 (僅供一次性傳遞給 Auth 服務加密)
     * @return 合法的 Tenant 聚合根
     */
    public static Tenant provisionNew(String companyName, PlanType planType, String adminEmail, String plainPassword) {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name cannot be empty");
        }

        // 預設給予 365 天合約
        Instant initialExpiry = Instant.now().plus(365, ChronoUnit.DAYS);

        Tenant newTenant = new Tenant(
                TenantId.generate(),
                companyName,
                planType,
                TenantStatus.ACTIVE,
                initialExpiry
        );

        // 核心：註冊領域事件！這個事件稍後會被轉為 OutboundEvent 送上 Kafka
        newTenant.registerEvent(new TenantProvisionedDomainEvent(
                UUID.randomUUID(),
                newTenant.getId().value(),
                companyName,
                adminEmail,
                plainPassword,
                planType.name(),
                LocalDateTime.now()
        ));

        return newTenant;
    }

    // ── 核心業務邏輯 (Domain Methods) ──

    /**
     * <b>【變更方法】升級 SaaS 方案</b>
     */
    public void upgradePlan(PlanType newPlan) {
        if (this.status != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Cannot upgrade plan for a non-active tenant");
        }
        if (newPlan.getLevel() <= this.planType.getLevel()) {
            throw new IllegalArgumentException("New plan must be an upgrade");
        }

        this.planType = newPlan;
        // 未來可擴充：this.registerEvent(new TenantPlanUpgradedEvent(...));
    }

    /**
     * <b>【管理業務】停權處分 (例如：欠費或違反服務條款)</b>
     */
    public void suspend(String reason) {
        if (this.status == TenantStatus.SUSPENDED) {
            return; // 冪等處理
        }
        this.status = TenantStatus.SUSPENDED;

        // 發布停權事件，通知 Gateway / Auth 服務阻斷該租戶的所有 Token
        this.registerEvent(new TenantSuspendedDomainEvent(
                UUID.randomUUID(),
                this.id.value(),
                LocalDateTime.now(),
                reason
        ));
    }

    /**
     * <b>【管理業務】復權 / 續約繳費</b>
     */
    public void reactivate(int extendDays) {
        this.status = TenantStatus.ACTIVE;
        this.expiryDate = this.expiryDate.plus(extendDays, ChronoUnit.DAYS);
        // 發布復權事件...
    }

    // ── 領域事件管理能力方法 ──

    private void registerEvent(DomainEvent event) {
        this.domainEvents.add(event);
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> clearedEvents = new ArrayList<>(this.domainEvents);
        this.domainEvents.clear();
        return clearedEvents;
    }

    // ── Getters ──
    public TenantId getId() { return id; }
    public String getCompanyName() { return companyName; }
    public PlanType getPlanType() { return planType; }
    public TenantStatus getStatus() { return status; }
    public Instant getExpiryDate() { return expiryDate; }
}