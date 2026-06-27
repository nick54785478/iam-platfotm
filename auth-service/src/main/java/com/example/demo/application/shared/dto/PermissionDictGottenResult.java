package com.example.demo.application.shared.dto;
/**
 * 輕量化讀取資料載體 (Data Transfer Object)
 */
public record PermissionDictGottenResult(
        String id,
        String code,
        String name,
        String description,
        String module
) {}