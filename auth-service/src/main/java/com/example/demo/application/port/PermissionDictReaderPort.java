package com.example.demo.application.port;

import com.example.demo.application.shared.dto.PermissionDictGottenResult;

import java.util.List;

/**
 * <h2>[應用層] 權限字典查詢合約 (Read Model Port)</h2>
 * <p>專供 UI 查詢使用，與寫入端的 Command Service 徹底物理隔離。</p>
 */
public interface PermissionDictReaderPort {

    /**
     * 依據條件查詢權限字典列表
     *
     * @param tenantId 租戶識別碼 (必填防禦)
     * @param module   模組名稱 (選填，如 "Department")
     * @param keyword  模糊搜尋關鍵字 (選填，比對 code 或 name)
     * @return 權限視圖 DTO 列表
     */
    List<PermissionDictGottenResult> searchPermissions(String tenantId, String module, String keyword);

}