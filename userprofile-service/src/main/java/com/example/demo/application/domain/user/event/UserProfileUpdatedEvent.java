package com.example.demo.application.domain.user.event;

import com.example.demo.application.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <b>[領域事件] 使用者個人檔案已更新</b>
 * <p>利用 Java Record 確保絕對不可變性 (Immutability)，並天然契合 DomainEvent 介面契約。</p>
 */
public record UserProfileUpdatedEvent(
        // --- 基礎設施/架構層面元數據 (天然實作 DomainEvent 介面) ---
        UUID eventId,
        String aggregateId,
        LocalDateTime occurredAt,

        // --- 業務負載 (Payload) ---
        String tenantId,
        String displayName,
        String avatarUrl,
        String bio,
        String language,
        String theme,
        String operator,
        Long version
) implements DomainEvent {

    /**
     * <b>領域層專用建構子 (Overloaded Constructor)</b>
     * <p>供聚合根發射事件時使用，自動生成唯一的 eventId 與當下時間，隱藏基礎設施細節。</p>
     */
    public UserProfileUpdatedEvent(String tenantId, String aggregateId, String displayName,
                                   String avatarUrl, String bio, String language,
                                   String theme, String operator, Long version) {
        // 必須呼叫 Record 的 Canonical Constructor (標準建構子)
        this(UUID.randomUUID(), aggregateId, LocalDateTime.now(), tenantId, displayName,
                avatarUrl, bio, language, theme, operator, version
        );
    }

    /**
     * 實作 DomainEvent 介面契約 (硬編碼聚合類型)
     * <p>因為這是類別層級的靜態概念，不需要宣告為 Record 的欄位浪費記憶體，直接回傳即可。</p>
     */
    @Override
    public String aggregateType() {
        return "UserProfile";
    }
}