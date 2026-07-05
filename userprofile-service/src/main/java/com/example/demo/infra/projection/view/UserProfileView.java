package com.example.demo.infra.projection.view;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <h2>[讀取端 - 視圖] 使用者個人檔案扁平投影 (Read Model)</h2>
 * <p>專為高頻 UI 查詢優化，無任何複雜關聯與領域邏輯。</p>
 */
@Entity
@Table(name = "user_profile_views")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 封鎖預設建構子，強迫使用靜態工廠
public class UserProfileView {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "language", nullable = false)
    private String language;

    @Column(name = "theme", nullable = false)
    private String theme;

    /**
     * 投影端生命線：事件版本號 (用來防禦 Kafka 亂序與重試的舊事件)
     */
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * <b>【工廠方法】處理首筆建立事件</b>
     */
    public static UserProfileView createNew(
            String id, String tenantId, String displayName, String avatarUrl,
            String bio, String language, String theme, Long version) {

        UserProfileView view = new UserProfileView();
        view.id = id;
        view.tenantId = tenantId;
        view.displayName = displayName;
        view.avatarUrl = avatarUrl;
        view.bio = bio;
        view.language = language;
        view.theme = theme;
        view.version = version;
        return view;
    }

    /**
     * <b>【視圖同步】處理後續更新事件 (自帶版本防禦牆)</b>
     *
     * @return true 代表同步成功並需落盤；false 代表這是過期事件，直接拋棄
     */
    public boolean syncDetails(
            String displayName, String avatarUrl, String bio,
            String language, String theme, Long eventVersion) {

        // 🛡️ 絕對防禦：如果收到的事件版本「小於等於」目前資料庫的版本，代表是舊訊息，直接吃掉
        if (eventVersion <= this.version) {
            return false;
        }

        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.bio = bio;
        this.language = language;
        this.theme = theme;
        this.version = eventVersion; // 推進版本號

        return true;
    }
}