package security.port;


import security.dto.ApiResourceRuleGottenResult;

import java.util.List;

/**
 * <h2>[輸出埠] API 規則庫讀取合約 (Read Model)</h2>
 * <p>
 * 遵循 ISP (Interface Segregation Principle) 原則，專供查詢端 (如 {@code DynamicRuleQueryService}) 使用，
 * 阻斷任何修改資料的可能性，達成讀寫分離的架構目標。
 * </p>
 * <p>
 * <b>【實作責任說明】</b>：<br>
 * 本介面為「輸出埠」，具體實作必須由各微服務 (e.g., DeptService, AuthService) 於各自的
 * Infrastructure 層自行實作 (Adapter)。<br>
 * <b>原因如下：</b>
 * <ul>
 * <li><b>資料庫自治性</b>：各微服務擁有獨立的資料庫 schema 與 Entity 定義，Shared Kernel 無法預知各微服務的實體結構。</li>
 * <li><b>依賴反轉 (DIP)</b>：Shared Kernel 不應依賴任何具體的 JPA 實體類別，只能依賴抽象介面。</li>
 * <li><b>領域隔離</b>：各微服務可能存在特定的查詢過濾邏輯（例如只查詢特定 status 或進行權限聯表查詢），這些邏輯應留在本地領域中。</li>
 * </ul>
 * </p>
 */
public interface ApiResourceRuleQueryRepositoryPort {

    /**
     * 根據租戶與系統預設範圍，獲取所有啟用的 API 規則。
     *
     * @param tenantId     當前請求的租戶 ID (來自網關)
     * @param systemTenant 系統全域租戶 ID (通常為 "SYSTEM")
     * @return 符合條件的規則清單 (已降維轉換為共用 DTO)
     */
    List<ApiResourceRuleGottenResult> findRulesForTenantAndSystem(String tenantId, String systemTenant);

}