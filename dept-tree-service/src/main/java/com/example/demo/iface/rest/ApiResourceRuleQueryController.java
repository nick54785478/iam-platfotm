package com.example.demo.iface.rest;

import com.example.demo.application.service.ApiResourceRuleQueryService;
import com.example.demo.application.shared.dto.PageQueriedResult;
import com.example.demo.application.shared.dto.PagedApiResourceRuleGottenResult;
import com.example.demo.application.shared.query.SearchApiResourceRuleQuery;
import com.example.demo.iface.dto.res.ApiRulesSummaryGottenResource;
import com.example.demo.infra.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>[介面層] API 資源授權規則查詢台 (Query API)</h2>
 * <p>
 * 專供系統超級管理員檢視當前系統中的所有動態路由保護規則。<br>
 * 遵循 CQRS 讀寫分離架構，本控制器僅暴露 GET 安全操作。
 * </p>
 */
@RestController
@RequestMapping("/api/departments/api-rules")
@RequiredArgsConstructor
@RequiresPermission("platform:RULE_MANAGE") // 依然掛載最高防禦傘，確保一般人無法探測系統路由
public class ApiResourceRuleQueryController {

    private final ApiResourceRuleQueryService queryService;

    /**
     * 獲取分頁化且支援動態多條件檢索的 API 保護規則
     * <p>
     * <b>前端呼叫範例：</b>
     * {@code GET /api-rules?tenantId=SYSTEM&httpMethod=POST&pathPattern=dept&page=0&size=10&sort=priority,asc}
     * </p>
     */
    @GetMapping
    public ResponseEntity<ApiRulesSummaryGottenResource> getPagedRules(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "httpMethod", required = false) String httpMethod,
            @RequestParam(value = "pathPattern", required = false) String pathPattern,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {

        // 邊界防腐轉譯 (Anti-Corruption Layer)：
        // 將外部鬆散的 HTTP Query Parameters，收斂組裝為內部嚴謹的強型別 Query Object
        SearchApiResourceRuleQuery searchQuery = new SearchApiResourceRuleQuery(
                tenantId,
                httpMethod,
                pathPattern
        );

        PageQueriedResult<PagedApiResourceRuleGottenResult> pagedRules = queryService.getPagedRulesForAdmin(searchQuery, pageable);
        return ResponseEntity.ok(new ApiRulesSummaryGottenResource("200", "Success", pagedRules));
    }
}