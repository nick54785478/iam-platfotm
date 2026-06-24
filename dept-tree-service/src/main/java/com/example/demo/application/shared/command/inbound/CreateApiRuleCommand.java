package com.example.demo.application.shared.command.inbound;

// 指令 DTO (Record)
public record CreateApiRuleCommand(
        String httpMethod,
        String pathPattern,
        String requiredPermission,
        Integer priority
) {}