package com.example.demo.infra.projection.view;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.Objects;

/**
 * <h2>[基礎設施層/讀取端] 權限字典非同步投影物理對象 (Rich Projection PO)</h2>
 * <p>
 * <b>【充血重構】</b>：<br>
 * 本實體採用充血模型設計，拒絕淪為只有 Getter/Setter 的貧血外殼。<br>
 * 強制透過靜態工廠方法 {@link #createNew} 進行初始化，並由 {@link #syncDetails} 內聚更新邏輯。
 * </p>
 */
@Entity
@Table(
        name = "auth_permissions_dict",
        indexes = {
                @Index(name = "idx_auth_perm_tenant_code", columnList = "tenant_id, code", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 🛡️ 僅供 Hibernate 反射實例化使用，嚴禁外部隨意 new
public class PermissionDictView {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", length = 50, nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "code", length = 100, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "module", length = 50, nullable = false)
    private String module;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ========================================================================
    // 🏭 靜態工廠方法 (保證實體一旦被創建，就是完全合法的、滿血的狀態)
    // ========================================================================
    public static PermissionDictView createNew(
            String id, String tenantId, String code, String name, String description, String module) {

        PermissionDictView po = new PermissionDictView();
        po.id = Objects.requireNonNull(id, "ID 錨點不可為空");
        po.tenantId = Objects.requireNonNull(tenantId, "租戶 ID 不可為空");
        po.code = Objects.requireNonNull(code, "權限編碼合約不可為空");
        po.createdAt = Instant.now();

        // 複用內部的更新邏輯，確保驗證規則一致
        po.syncDetailsInternal(name, description, module);
        return po;
    }

    // ========================================================================
    // ⚔️ 充血業務行為 (Business Behaviors)
    // ========================================================================
    /**
     * 🚀 投影同步更新：當遠端變更事件抵達時，由此方法決定如何就地折疊狀態
     */
    public void syncDetails(String newName, String newDescription, String newModule) {
        this.syncDetailsInternal(newName, newDescription, newModule);
    }

    // ========================================================================
    // 🛡️ 內部防禦性驗證 (Internal Defensive Validation)
    // ========================================================================
    private void syncDetailsInternal(String name, String description, String module) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("權限顯示名稱不可為空");
        }
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("模組分類歸屬不可為空");
        }
        this.name = name;
        this.description = description;
        this.module = module;
        this.updatedAt = Instant.now(); // 每次同步時，就地更新時間戳
    }
}