package com.example.demo.application.port;

import com.example.demo.application.shared.dto.ApiResourceRuleGottenResult;

import java.util.List;

/**
 * <h2>[應用層 - 輸出埠] API 規則庫讀取合約 (Read Model)</h2>
 * <p>
 * 遵循 ISP 原則，專供查詢端 (Query Service) 使用，阻斷任何修改資料的可能性。
 * </p>
 */
public interface ApiResourceRuleReaderPort {
    /**
     * 撈取所有啟用的規則，並且必須按照 priority 由小到大嚴格排序！
     */
    List<ApiResourceRuleGottenResult> findAllActiveRulesSortedByPriority();
}