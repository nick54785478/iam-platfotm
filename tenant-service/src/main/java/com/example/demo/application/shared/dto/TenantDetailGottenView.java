package com.example.demo.application.shared.dto;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;

import java.time.Instant;

/**
 * <h2>[應用層] 租戶詳情唯讀視圖 (Query DTO)</h2>
 */
public record TenantDetailGottenView(
        String tenantId,
        String companyName,
        PlanType planType,
        TenantStatus status,
        Instant expiryDate
) {}