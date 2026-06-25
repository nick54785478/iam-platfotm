package com.example.demo.infra.apirule;

import com.example.demo.application.shared.dto.PagedApiResourceRuleGottenResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import security.dto.ApiResourceRuleGottenResult;


/**
 * <h2>[基礎設施層] API 資源權限規則實體 (多租戶版)</h2>
 */
@Entity
@Getter
@Table(name = "api_resource_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiResourceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 加入多租戶維度防護
    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

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

    // ==================================================
    // View Converters (視圖轉換)
    // ==================================================

    /**
     * 轉換為領域層的純紀錄 (補上 tenantId 供攔截器執行 Override 防禦)
     */
    public ApiResourceRuleGottenResult toDomain() {
        return new ApiResourceRuleGottenResult(
                this.tenantId,
                this.httpMethod,
                this.pathPattern,
                this.requiredPermission,
                this.priority
        );
    }

    /**
     * 轉換為後台管理介面專用視圖 (包含 ID、TenantId 與啟用狀態)
     */
    public PagedApiResourceRuleGottenResult toAdminView() {
        return new PagedApiResourceRuleGottenResult(
                this.id,
                this.tenantId,
                this.httpMethod,
                this.pathPattern,
                this.requiredPermission,
                this.priority,
                this.isActive
        );
    }

    // ==================================================
    // Business Behaviors (充血業務行為)
    // ==================================================

    /**
     * 業務工廠方法：建立全新規則 (強制要求傳入 tenantId)
     */
    public static ApiResourceRule createNew(String tenantId, String httpMethod, String pathPattern, String requiredPermission, Integer priority) {
        ApiResourceRule rule = new ApiResourceRule();
        rule.tenantId = tenantId; // 賦予租戶歸屬
        rule.httpMethod = httpMethod.toUpperCase();
        rule.pathPattern = pathPattern;
        rule.requiredPermission = requiredPermission;
        rule.priority = priority;
        rule.isActive = true;
        return rule;
    }

    /**
     * 狀態變更：更新規則內容
     * 架構防禦：刻意不傳入 tenantId，保護租戶邊界不可被橫向篡改
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