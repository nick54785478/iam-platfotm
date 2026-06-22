package com.example.demo.application.domain.shared.command;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;

/**
 * <h2>[應用層] 租戶升級指令</h2>
 */
public record UpgradePlanCommand(
        String tenantId,
        PlanType newPlanType
) {}