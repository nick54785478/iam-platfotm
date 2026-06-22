package com.example.demo.application.domain.shared.command;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;

/**
 * <h2>[應用層] 租戶入駐指令</h2>
 */
public record ProvisionTenantCommand(
        String companyName,
        PlanType planType,
        String adminEmail,
        String plainPassword // 初始明文密碼，將隨領域事件送往 Auth 服務加密
) {}