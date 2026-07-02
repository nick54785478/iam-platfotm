package com.example.demo.application.shared.dto;

/**
 * 根部門唯讀輕量資料載體
 */
public record DepartmentRootGottenResult (
        String id,
        String code,
        String name,
        String status,
        int sortOrder,
        int directHeadcount,
        int totalHeadcount
) {}
