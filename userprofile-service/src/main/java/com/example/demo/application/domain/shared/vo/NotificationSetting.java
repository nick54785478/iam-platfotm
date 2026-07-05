package com.example.demo.application.domain.shared.vo;

/**
 * <b>[值物件] 通知管道開關矩陣</b>
 */
public record NotificationSetting(boolean email, boolean inApp, boolean sms) {
    /**
     * 工廠方法：預設全開
     */
    public static NotificationSetting allEnabled() {
        return new NotificationSetting(true, true, true);
    }
    
    /**
     * 工廠方法：預設全關
     */
    public static NotificationSetting allDisabled() {
        return new NotificationSetting(false, false, false);
    }
}