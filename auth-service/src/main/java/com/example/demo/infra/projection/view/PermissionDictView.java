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
 * <b>【充血重構與冪等防禦】</b>：<br>
 * 本實體採用充血模型設計，拒絕淪為只有 Getter/Setter 的貧血外殼。<br>
 * 強制透過靜態工廠方法 {@link #createNew} 進行初始化。<br>
 * 內建 <b>高水位線版本防禦 (High-Water Mark)</b>，由 {@link #syncDetails} 嚴格把關，徹底免疫 Kafka 亂序與重複消費。
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
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 僅供 Hibernate 反射實例化使用，嚴禁外部隨意 new
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

    /**
     * 投影版本號 (承接自 DeptService 領域事件的 Aggregate Version)
     */
    @Column(name = "version", nullable = false)
    private long version;

    // ========================================================================
    // 靜態工廠方法 (保證實體一旦被創建，就是完全合法的、滿血的狀態)
    // ========================================================================
    public static PermissionDictView createNew(
            String id, String tenantId, String code, String name, String description, String module, long version) {

        PermissionDictView po = new PermissionDictView();
        po.id = Objects.requireNonNull(id, "ID 錨點不可為空");
        po.tenantId = Objects.requireNonNull(tenantId, "租戶 ID 不可為空");
        po.code = Objects.requireNonNull(code, "權限編碼合約不可為空");
        po.createdAt = Instant.now();

        // 關鍵修復：初始化時必須將版本號寫入，建立初始水位線！
        po.version = version;

        // 複用內部的更新邏輯，確保驗證規則一致
        po.syncDetailsInternal(name, description, module);
        return po;
    }

    // ========================================================================
    // 充血業務行為 (Business Behaviors)
    // ========================================================================

    /**
     * 投影同步更新：帶有版本防禦機制的狀態折疊
     *
     * @param eventVersion 來自 Kafka 領域事件的版本號
     * @return boolean 若事件版本較新且更新成功則回傳 true；若為過期/重複事件則拒絕更新並回傳 false。
     */
    public boolean syncDetails(String newName, String newDescription, String newModule, long eventVersion) {

        //  核心防禦：亂序與重複事件防護 (樂觀丟棄)
        if (eventVersion <= this.version) {
            return false;
        }

        // 執行狀態更新
        this.syncDetailsInternal(newName, newDescription, newModule);

        // 水位線推進：將實體版本更新為最新的事件版本
        this.version = eventVersion;
        return true;
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