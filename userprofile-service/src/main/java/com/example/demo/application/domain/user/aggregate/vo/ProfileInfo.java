package com.example.demo.application.domain.user.aggregate.vo;


import org.springframework.util.StringUtils;

/**
 * <b>[值物件] 個人基礎顯示資訊</b>
 * <p>封裝與視覺呈現相關的基礎屬性，確保資料的完整性與合法性。</p>
 */
public record ProfileInfo(String displayName, String avatarUrl, String bio) {
    public ProfileInfo {
        if (!StringUtils.hasText(displayName)) {
            throw new IllegalArgumentException("DisplayName cannot be empty");
        }
        // 可以在此處加入 URL 格式驗證或 bio 長度限制
        if (bio != null && bio.length() > 500) {
            throw new IllegalArgumentException("Bio length cannot exceed 500 characters");
        }
    }

    /**
     * 預設的工廠方法，當使用者剛註冊時，可用預設資訊初始化
     */
    public static ProfileInfo defaultOf(String defaultDisplayName) {
        return new ProfileInfo(defaultDisplayName, null, null);
    }
}