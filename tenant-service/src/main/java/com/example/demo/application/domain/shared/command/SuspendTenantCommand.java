package com.example.demo.application.domain.shared.command;

/**
 * <h2>[應用層] 租戶停權指令</h2>
 */
public record SuspendTenantCommand(
        String tenantId,
        String reason
) {}