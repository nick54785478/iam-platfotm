package com.example.demo.application.service;

import com.example.demo.application.port.ApiResourceRuleReaderPort;
import com.example.demo.application.shared.dto.PageQueriedResult;
import com.example.demo.application.shared.dto.PagedApiResourceRuleGottenResult;
import com.example.demo.application.shared.query.SearchApiResourceRuleQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * <h2>[應用層] API 動態資源規則查詢服務 (Query Service)</h2>
 * <p>
 * <b>【架構特性】</b>：純讀取端服務，不包含任何業務變更邏輯。<br>
 * 直接透傳至基礎設施取得 View DTO，捨棄不必要的快取層，確保後台管理介面呈現即時強一致性資料。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 宣告唯讀，優化 Hibernate Session 與資料庫連線效能
public class ApiResourceRuleQueryService {

    private final ApiResourceRuleReaderPort readerPort;

    /**
     * 獲取所有 API 規則清單 (供後台列表渲染)
     * <p>
     * <b>【架構解耦】</b>：將底層 Spring Data 的 Page 結構，轉換為型別安全且去框架化的 PageQueriedResult DTO。
     * </p>
     */
    public PageQueriedResult<PagedApiResourceRuleGottenResult> getPagedRulesForAdmin(
            SearchApiResourceRuleQuery query, Pageable pageable) {

        log.debug("[Authz-Query] 正在分頁檢索 API 規則，當前頁碼: {}", pageable.getPageNumber());

        // 1. 依然讓底層 Port/Adapter 處理分頁與 Specification 動態查詢，獲取 Spring 原生 Page
        Page<PagedApiResourceRuleGottenResult> rawPage = readerPort.findPagedRulesForAdmin(query, pageable);

        // 2. 實施防腐轉譯：將 Spring 框架特徵剝離，精準組裝成你的 PageQueriedResult
        return new PageQueriedResult<>(
                rawPage.getContent(),       // 實際的資料清單 (List<T>)
                rawPage.getTotalElements(), // 總筆數
                rawPage.getTotalPages(),    // 總頁數
                rawPage.getNumber()         // 當前頁碼 (Spring 預設從 0 開始，若前端習慣從 1 開始，可在此處 +1)
        );
    }
}