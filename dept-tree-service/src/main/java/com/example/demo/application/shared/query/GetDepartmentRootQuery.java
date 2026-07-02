package com.example.demo.application.shared.query;


import java.util.Objects;

/**
 * <h2>[應用層] 部門根節點查詢指令 (Query Object)</h2>
 * <p>
 * 將零散的查詢條件封裝為單一的 Query 物件，解決長參數列問題。
 * 內建自體防禦檢核，確保租戶隔離底線不被打破。
 * </p>
 */
public record GetDepartmentRootQuery(
        String tenantId,
        String code,
        String name,
        int page,
        int size
) {
    public GetDepartmentRootQuery {
        // 🛡️ 絕對防禦：租戶 ID 絕對不可為空，防堵越權查詢漏洞 (IDOR)
        Objects.requireNonNull(tenantId, "TenantId is required for Root Query");

        // 防禦不合理的分頁參數
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
    }
}