package com.example.demo.iface.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * <h2>[網關層 - 通用傳輸物件] 全局標準化響應結構體 (SaaS 規範版)</h2>
 * * @param <T> 攜帶之業務數據類型
 * @param code 業務級錯誤/狀態代碼 (非僅僅是 HTTP 狀態碼，便於前端精準識別)
 * @param message 友好的業務提示訊息
 * @param data 實際返還之數據本體 (若無數據則不參與序列化或返還 null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // 關鍵：若 data 為 null，轉 JSON 時自動隱藏該欄位，保持 Payload 乾淨
public record GatewayResponse<T>(
        String code,
        String message,
        T data
) {
    /**
     * 快捷工廠：構建純粹的業務失敗或安全降級響應 (無數據體)
     */
    public static <T> GatewayResponse<T> fail(String code, String message) {
        return new GatewayResponse<>(code, message, null);
    }

    /**
     * 快捷工廠：構建帶有安全降維數據的降級響應 (例如防護性的空陣列)
     */
    public static <T> GatewayResponse<T> fallback(String code, String message, T fallbackData) {
        return new GatewayResponse<>(code, message, fallbackData);
    }
}