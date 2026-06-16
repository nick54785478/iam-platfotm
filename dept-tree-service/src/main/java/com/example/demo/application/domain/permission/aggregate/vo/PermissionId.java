package com.example.demo.application.domain.permission.aggregate.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionId implements Serializable {

    private String value;

    public PermissionId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PermissionId cannot be null or blank");
        }
        this.value = value;
    }

    /**
     * 🌟 領域自決：提供專屬的 ID 生成策略
     */
    public static PermissionId generate() {
        return new PermissionId(UUID.randomUUID().toString());
    }
}