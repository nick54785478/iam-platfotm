package com.example.demo.iface.dto.req;

// 指令 DTO (Record)
public record CreateApiRuleResource(
        String httpMethod,
        String pathPattern,
        String requiredPermission,
        Integer priority
) {}