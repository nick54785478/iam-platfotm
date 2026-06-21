package com.example.demo.application.domain.tenant.aggregate.vo;

import java.util.UUID;

public record TenantId(String value) {
    public TenantId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TenantId cannot be null or empty");
        }
    }
    public static TenantId generate() {
        // SaaS 系統的 Tenant ID 通常會加上前綴以利辨識，例如 "T-8f9a..."
        return new TenantId("T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}