package com.example.demo.application.service;

import com.example.demo.application.domain.shared.command.ProvisionTenantCommand;
import com.example.demo.application.domain.shared.command.SuspendTenantCommand;
import com.example.demo.application.domain.shared.command.UpgradePlanCommand;
import com.example.demo.application.domain.tenant.aggregate.Tenant;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantId;
import com.example.demo.application.port.TenantStorerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>[應用層] 租戶寫入側應用服務 (Tenant Command Service)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 扮演 Use Case 的協調者。負責開啟本地事務，載入聚合根，
 * 觸發充血模型的業務行為，並交由 Writer Port 進行持久化與事件派發。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantCommandService {

    private final TenantStorerPort tenantStorer;

    /**
     * <b>【使用案例】全新企業租戶入駐 (Onboarding)</b>
     *
     * @return 成功建立的租戶唯一識別碼 (TenantId) 字串
     */
    @Transactional
    public String provisionTenant(ProvisionTenantCommand command) {
        log.info("[Tenant-App] 準備開通新租戶: {}", command.companyName());

        // 1. 呼叫聚合根的靜態工廠方法，執行商業邏輯並內聚產生 TenantProvisionedDomainEvent
        Tenant newTenant = Tenant.provisionNew(
                command.tenantId(),
                command.companyName(),
                command.planType(),
                command.adminEmail(),
                command.plainPassword()
        );

        // 2. 透過 Port 儲存至 DB。
        // 魔法發生處：此處的 save 會觸發 Adapter 發射事件，
        // 並被 OutboxListener 在 BEFORE_COMMIT 階段攔截寫入 outbox_events。
        tenantStorer.save(newTenant);

        log.info("[Tenant-App] 租戶開通成功，TenantId: {}", newTenant.getId().value());
        return newTenant.getId().value();
    }

    /**
     * <b>【使用案例】將租戶停權</b>
     */
    @Transactional
    public void suspendTenant(SuspendTenantCommand command) {
        // 1. 透過 Port 查出聚合根 (若找不到則拋出業務異常，可由 Controller Advice 統一捕捉為 404)
        Tenant tenant = tenantStorer.findById(new TenantId(command.tenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + command.tenantId()));

        // 2. 呼叫聚合根行為：變更狀態並註冊 TenantSuspendedDomainEvent
        tenant.suspend(command.reason());

        // 3. 持久化並發射事件
        tenantStorer.save(tenant);

        log.info("[Tenant-App] 租戶已被停權，TenantId: {}, 原因: {}", command.tenantId(), command.reason());
    }

    /**
     * <b>【使用案例】升級租戶方案</b>
     */
    @Transactional
    public void upgradeTenantPlan(UpgradePlanCommand command) {
        Tenant tenant = tenantStorer.findById(new TenantId(command.tenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + command.tenantId()));

        // 呼叫聚合根行為，嚴格校驗升級邏輯
        tenant.upgradePlan(command.newPlanType());

        tenantStorer.save(tenant);

        log.info("[Tenant-App] 租戶方案升級成功，TenantId: {}, 新方案: {}", command.tenantId(), command.newPlanType());
    }
}