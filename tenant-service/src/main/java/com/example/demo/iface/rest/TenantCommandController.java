package com.example.demo.iface.rest;

import com.example.demo.application.domain.shared.command.ProvisionTenantCommand;
import com.example.demo.application.domain.shared.command.SuspendTenantCommand;
import com.example.demo.application.domain.shared.command.UpgradePlanCommand;
import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.service.TenantCommandService;
import com.example.demo.iface.dto.req.TenantRequest;
import com.example.demo.iface.dto.res.TenantResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>[介面層] 租戶管理 API 端點 (SaaS Admin API)</h2>
 * <p>
 * 專供平台超級管理員 (Platform Admin) 或是自動化化計費系統呼叫。
 * 負責將 HTTP 請求轉換為 Application Command，徹底隔離 Web 技術。
 * </p>
 */
@RestController
@RequestMapping("/api/platform/tenants")
@RequiredArgsConstructor
public class TenantCommandController {

    private final TenantCommandService commandService;

    /**
     * <b>開通全新企業租戶</b>
     */
    @PostMapping
    public ResponseEntity<TenantResponse.TenantProvisionedResource> provisionTenant(
            @Valid @RequestBody TenantRequest.ProvisionTenantResource request) {

        // 1. 將 Web DTO 轉換為 Application Command (防腐層轉換)
        ProvisionTenantCommand command = new ProvisionTenantCommand(
                request.tenantId(),
                request.companyName(),
                PlanType.valueOf(request.planType()) ,
                request.adminEmail(),
                request.plainPassword()
        );

        // 2. 委託給 Application Layer 執行
        String newTenantId = commandService.provisionTenant(command);

        // 3. 回傳標準 HTTP 201 Created 回應
        return ResponseEntity.status(HttpStatus.CREATED).body(new TenantResponse.TenantProvisionedResource(
                "200","Tenant provisioned successfully. Background initialization has started.",
                newTenantId
        ));
    }

    /**
     * <b>將指定租戶停權</b>
     */
    @PostMapping("/{tenantId}/suspend")
    public ResponseEntity<TenantResponse.TenantSuspendedResource> suspendTenant(
            @PathVariable("tenantId") String tenantId,
            @Valid @RequestBody TenantRequest.SuspendTenantResource request) {

        SuspendTenantCommand command = new SuspendTenantCommand(tenantId, request.reason());
        commandService.suspendTenant(command);

        return ResponseEntity.ok(new TenantResponse.TenantSuspendedResource("200", "Success")); // HTTP 204
    }

    /**
     * <b>升級指定租戶的計費方案</b>
     * 這裡示範用 Query Parameter 傳遞單一值，也可改用 RequestBody
     */
    @PutMapping("/{tenantId}/plan")
    public ResponseEntity<TenantResponse.TenantUpgradedResource> upgradePlan(
            @PathVariable("tenantId") String tenantId,
            @RequestParam("newPlan") PlanType newPlanType) {

        UpgradePlanCommand command = new UpgradePlanCommand(tenantId, newPlanType);
        commandService.upgradeTenantPlan(command);

        return ResponseEntity.ok(new TenantResponse.TenantUpgradedResource("200", "Success")); // HTTP 204
    }
}