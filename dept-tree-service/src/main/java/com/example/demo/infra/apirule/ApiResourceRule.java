package com.example.demo.infra.apirule;

import com.example.demo.application.shared.dto.ApiResourceRuleGottenResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <h2>[基礎設施層] API 資源權限規則實體</h2>
 */
@Entity
@Getter
@Table(name = "api_resource_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiResourceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "path_pattern", nullable = false)
    private String pathPattern;

    @Column(name = "required_permission", nullable = false, length = 128)
    private String requiredPermission;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    // 轉換為領域層的純紀錄
    public ApiResourceRuleGottenResult toDomain() {
        return new ApiResourceRuleGottenResult(
                this.httpMethod,
                this.pathPattern,
                this.requiredPermission,
                this.priority
        );
    }

    /**
     * 業務工廠方法：建立全新規則
     */
    public static ApiResourceRule createNew(String httpMethod, String pathPattern, String requiredPermission, Integer priority) {
        ApiResourceRule rule = new ApiResourceRule();
        rule.httpMethod = httpMethod.toUpperCase();
        rule.pathPattern = pathPattern;
        rule.requiredPermission = requiredPermission;
        rule.priority = priority;
        rule.isActive = true; // 預設啟用
        return rule;
    }

    /**
     * 狀態變更：更新規則內容
     */
    public void update(String httpMethod, String pathPattern, String requiredPermission, Integer priority) {
        this.httpMethod = httpMethod.toUpperCase();
        this.pathPattern = pathPattern;
        this.requiredPermission = requiredPermission;
        this.priority = priority;
    }

    /**
     * 狀態變更：啟用 / 停用 (軟刪除)
     */
    public void toggleActiveStatus(boolean isActive) {
        this.isActive = isActive;
    }
}