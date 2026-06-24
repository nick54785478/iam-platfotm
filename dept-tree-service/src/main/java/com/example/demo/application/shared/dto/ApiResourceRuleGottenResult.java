package com.example.demo.application.shared.dto;

/**
 * <h2>[領域層] API 資源保護規則值物件 (純潔 POJO)</h2>
 */
public record ApiResourceRuleGottenResult(
        String httpMethod,
        String pathPattern,
        String requiredPermissionCode,
        int priority
) {}