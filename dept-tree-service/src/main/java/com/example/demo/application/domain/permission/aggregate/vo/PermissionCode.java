package com.example.demo.application.domain.permission.aggregate.vo;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.regex.Pattern;

/**
 * 權限代碼值物件 (Value Object)
 */
@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 規範要求
public class PermissionCode implements Serializable {

    private String value;

    // 💡 正規表示式：前半段為服務名稱 (允許小寫英數字與連字號)，後半段為角色功能 (允許大寫英文與底線)
    // 範例匹配：dept-service:ADMIN_ALL, auth-service:USER_READ
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9-]+:[A-Z0-9_]+$");

    public PermissionCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PermissionCode cannot be null or blank");
        }

        String trimmedValue = value.trim();
        if (!CODE_PATTERN.matcher(trimmedValue).matches()) {
            throw new IllegalArgumentException(
                    "PermissionCode format is invalid. It must follow 'service-name:ROLE_FUNCTION' pattern (e.g., 'dept-service:ADMIN_ALL'). Provided: " + trimmedValue
            );
        }

        this.value = trimmedValue;
    }
}
