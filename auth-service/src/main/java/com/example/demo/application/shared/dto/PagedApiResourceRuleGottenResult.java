package com.example.demo.application.shared.dto;

/**
 * <h2>[應用層 - 讀取端] API 規則管理台專用視圖 (View)</h2>
 * <p>
 * 專供前端後台管理介面渲染列表使用，包含實體 ID 與啟用狀態。
 * </p>
 */
public record PagedApiResourceRuleGottenResult(
        Long id,
        String tenantId,
        String httpMethod,
        String pathPattern,
        String requiredPermission,
        Integer priority,
        Boolean isActive
) {}