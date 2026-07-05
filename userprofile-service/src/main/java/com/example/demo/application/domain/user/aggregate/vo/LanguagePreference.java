package com.example.demo.application.domain.user.aggregate.vo;

/**
 * <b>[值物件/枚舉] 使用者介面語系偏好</b>
 */
public enum LanguagePreference {
    ZH_TW("zh-TW"),
    EN_US("en-US"),
    JA_JP("ja-JP");

    private final String code;

    LanguagePreference(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
