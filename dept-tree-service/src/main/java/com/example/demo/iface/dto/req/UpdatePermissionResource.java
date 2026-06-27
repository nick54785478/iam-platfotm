package com.example.demo.iface.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 修改權限細節專用 HTTP Resource
 */
public record UpdatePermissionResource(
        @NotBlank(message = "權限區域名稱不可為空")
        String name,

        String description,

        @NotBlank(message = "必須明確指定權限所屬系統模組")
        String module
) {
}