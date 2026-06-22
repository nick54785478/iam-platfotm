package com.example.demo.application.shared.dto;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;

/**
 * <h2>[應用層] 租戶清單摘要視圖 (Query DTO - 用於分頁列表)</h2>
 */
public record TenantSummarySearchedView(
        String tenantId,
        String companyName,
        PlanType planType,
        TenantStatus status
) {}