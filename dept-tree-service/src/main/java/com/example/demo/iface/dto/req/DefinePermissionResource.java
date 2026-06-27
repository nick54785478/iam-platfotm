package com.example.demo.iface.dto.req;

import jakarta.validation.constraints.NotBlank;

/**
 * 宣告權限專用 HTTP Resource
 */
public record DefinePermissionResource(
        @NotBlank(message = "權限代碼為核心校驗欄位，不可為空")
        String code,

        @NotBlank(message = "權限區域名稱不可為空")
        String name,

        String description,

        @NotBlank(message = "必須明確指定權限所屬系統模組")
        String module
) {
}