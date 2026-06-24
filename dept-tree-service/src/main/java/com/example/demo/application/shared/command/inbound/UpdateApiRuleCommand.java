package com.example.demo.application.shared.command.inbound;

public record UpdateApiRuleCommand(
        Long ruleId,
        String httpMethod,
        String pathPattern,
        String requiredPermission,
        Integer priority
) {}