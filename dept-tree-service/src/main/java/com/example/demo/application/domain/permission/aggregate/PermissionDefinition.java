package com.example.demo.application.domain.permission.aggregate;

import com.example.demo.application.domain.permission.aggregate.vo.PermissionCode;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionId;
import com.example.demo.application.domain.permission.event.PermissionDefinitionCreatedEvent;
import com.example.demo.application.domain.permission.event.PermissionDefinitionUpdatedEvent;
import com.example.demo.application.domain.shared.core.BaseAggregateRoot;
import com.example.demo.application.domain.shared.vo.TenantId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * <h2>[領域層/寫入端] 權限定義聚合根 (Permission Definition Aggregate Root)</h2>
 * <p>
 * <b>【架構定位與職責】</b>：<br>
 * 此實體僅負責在子系統 (DeptService) 內「宣告」與「定義」本模組所擁有的資源權限。<br>
 * 不負責記錄任何 User 或 Role 的綁定關係（綁定關係隸屬於 AuthService 的聚合邊界）。<br>
 * <b>事件驅動 (Event-Driven)：</b> 當此實體發生狀態或屬性變更時，會將變更化為領域事件，
 * 供底層基礎設施透過 Outbox Pattern 派發至 Kafka，通知 AuthService 同步最新的權限字典。
 * </p>
 */
@Getter
@Entity
@Table(
        name = "sys_permissions",
        uniqueConstraints = {
                // 物理防線：確保同一個租戶下的「權限代碼」絕對不會重複
                @UniqueConstraint(name = "uk_tenant_code", columnNames = {"tenant_id", "code"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 僅供 Hibernate 反射使用，嚴禁外部隨意 new
public class PermissionDefinition extends BaseAggregateRoot {

    /**
     * 權限唯一識別碼 (Aggregate ID)
     */
    @EmbeddedId
    // 🌟 加上這行：明確將 VO 的 value 屬性映射到資料庫的 id 欄位，避開保留字衝突
    @AttributeOverride(name = "value", column = @Column(name = "id", length = 36, nullable = false))
    private PermissionId id;

    /**
     * 租戶隔離 ID (SaaS 架構邊界防護)
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", length = 50, nullable = false, updatable = false))
    private TenantId tenantId;

    /**
     * 權限唯一代碼，例如 "dept-service:ADMIN_ALL"。
     * <p>一經建立絕對不可變更 (Immutable)，作為微服務間權限檢核的唯一合約。</p>
     */
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "code", length = 100, nullable = false, updatable = false))
    private PermissionCode code;

    /**
     * 顯示名稱，例如 "部門編輯權限" (供前端 UI 與 AuthService 管理後台顯示使用)
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * 詳細描述，說明此權限的具體影響範圍
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 所屬模組歸類，例如 "Department", "Employee"
     */
    @Column(name = "module", length = 50, nullable = false)
    private String module;

    /**
     * 樂觀鎖版本號 (防堵併發覆寫危機)
     */
    @Version
    private long version;

    // ==================================================
    // 審計稽核軌跡 (Audit Trail Attributes)
    // ==================================================

    /**
     * 權限宣告建立時間
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 宣告此權限的系統管理員或操作者 ID
     */
    @Column(name = "created_by", nullable = false, updatable = false, length = 100)
    private String createdBy;

    /**
     * 權限最後異動更新時間
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 最後執行變更操作的管理員或操作者 ID
     */
    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    // ==================================================
    // Constructor (嚴密閉合的建構子)
    // ==================================================

    private PermissionDefinition(
            TenantId tenantId, PermissionId id, PermissionCode code,
            String name, String description, String module,
            Instant createdAt, String createdBy, Instant updatedAt, String updatedBy) {

        this.tenantId = Objects.requireNonNull(tenantId, "TenantId is required");
        this.id = Objects.requireNonNull(id, "PermissionId is required");
        this.code = Objects.requireNonNull(code, "PermissionCode is required");
        changeDetailsInternal(name, description, module);

        this.createdAt = Objects.requireNonNull(createdAt);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.updatedBy = Objects.requireNonNull(updatedBy);
    }

    // ==================================================
    // Factory Methods (充血領域工廠)
    // ==================================================

    /**
     * 創建一個全新的權限定義。
     * (強制要求輸入所有必要屬性，保證實體一旦建立就是「合法滿血」的狀態)
     *
     * @param operator 執行宣告操作的管理員/系統 ID
     */
    public static PermissionDefinition declare(
            TenantId tenantId, PermissionCode code,
            String name, String description, String module, String operator) {

        PermissionId id = PermissionId.generate();
        Instant now = Instant.now();

        PermissionDefinition permission = new PermissionDefinition(
                tenantId, id, code, name, description, module, now, operator, now, operator
        );

        // 發布建立事件，準備廣播給 AuthService 註冊新權限
        permission.raise(new PermissionDefinitionCreatedEvent(tenantId.getValue(), id.getValue(),
                code.getValue(), name, description, module, operator, permission.version));

        return permission;
    }

    // ==================================================
    // Business Behaviors (充血業務行為)
    // ==================================================

    /**
     * 變更權限的顯示名稱、描述與所屬模組歸類。
     * 💡 備註：code (權限代碼) 是不可變更的 (Immutable)，以防止下游 AuthService 解析錯亂。
     *
     * @param newName        新顯示名稱
     * @param newDescription 新描述
     * @param newModule      新所屬模組歸類
     * @param operator       操作者 ID
     */
    public void updateDetails(String newName, String newDescription, String newModule, String operator) {
        changeDetailsInternal(newName, newDescription, newModule);
        touch(operator);

        // 在發射事件前，讓記憶體內的版本號明確手動 +1
        this.version++;

        // 發布更新事件，通知 AuthService 同步最新的名稱、描述與模組歸類
        raise(new PermissionDefinitionUpdatedEvent(
                this.tenantId.getValue(), this.id.getValue(), this.code.getValue(),
                newName, newDescription, newModule, operator, version
        ));
    }

    // ==================================================
    // Internal Validations (聚合內部防禦與更新)
    // ==================================================

    private void changeDetailsInternal(String name, String description, String module) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Permission name cannot be blank");
        }
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("Module classification is required");
        }
        this.name = name;
        this.description = description;
        this.module = module;
    }

    private void touch(String operator) {
        this.updatedAt = Instant.now();
        this.updatedBy = operator;
    }
}