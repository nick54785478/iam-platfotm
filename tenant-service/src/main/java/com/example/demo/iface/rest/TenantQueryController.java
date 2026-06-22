package com.example.demo.iface.rest;


import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;
import com.example.demo.application.service.TenantQueryService;
import com.example.demo.application.shared.dto.PagedQueriedView;
import com.example.demo.application.shared.dto.TenantDetailGottenView;
import com.example.demo.application.shared.dto.TenantSummarySearchedView;
import com.example.demo.iface.dto.res.TenantResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>[介面層] 租戶視圖查詢 API 端點 (SaaS Query API)</h2>
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
public class TenantQueryController {

    private final TenantQueryService tenantQueryService;

    /**
     * <b>[GET] 根據識別碼單查租戶詳情</b>
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantResponse.TenantDetailGottenResource> getTenantById(@PathVariable("tenantId") String tenantId) {
        TenantDetailGottenView detail = tenantQueryService.getTenantDetail(tenantId);
        return ResponseEntity.ok(new TenantResponse.TenantDetailGottenResource("200", "Success", detail));
    }

    /**
     * <b>[GET] 萬能多條件分頁查詢租戶列表</b>
     * 呼叫範例：{@code GET /api/v1/platform/tenants?companyName=威朋&planType=ENTERPRISE&page=0&size=10}
     */
    @GetMapping
    public ResponseEntity<TenantResponse.TenantSummarySearchedResource> searchTenants(
            @RequestParam(value = "companyName", required = false) String companyName,
            @RequestParam(value = "planType", required = false) PlanType planType,
            @RequestParam(value = "status", required = false) TenantStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        // 回傳乾淨的 PagedQueriedView，徹底隱藏底層使用的持久化技術
        PagedQueriedView<TenantSummarySearchedView> results = tenantQueryService.searchTenants(
                companyName, planType, status, page, size
        );

        return ResponseEntity.ok(new TenantResponse.TenantSummarySearchedResource("200", "Success", results));
    }
}
