package com.example.demo.iface.dto.req;

public record UpdateApiRuleResource(
        String httpMethod,
        String pathPattern,
        String requiredPermission,
        Integer priority
) {}