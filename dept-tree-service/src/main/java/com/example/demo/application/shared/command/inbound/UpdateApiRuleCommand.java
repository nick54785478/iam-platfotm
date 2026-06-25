package com.example.demo.application.shared.command.inbound;

public record UpdateApiRuleCommand(
        String tenantId,
        Long ruleId,
        String httpMethod,
        String pathPattern,
        String requiredPermission,
        Integer priority,
        String operator
) {}