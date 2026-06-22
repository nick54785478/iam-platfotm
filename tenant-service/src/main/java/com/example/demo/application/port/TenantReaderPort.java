package com.example.demo.application.port;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;
import com.example.demo.application.shared.dto.PagedQueriedView;
import com.example.demo.application.shared.dto.TenantDetailGottenView;
import com.example.demo.application.shared.dto.TenantSummarySearchedView;

import java.util.Optional;

/**
 * <h2>[應用層 - 輸出埠] 租戶唯讀視圖查詢埠</h2>
 * <p>
 * <b>【零框架侵入】</b>：<br>
 * 本合約徹底剔除 Spring Data 的 Page 與 Pageable 依賴，
 * 確保應用層的絕對純潔性與可移植性。
 * </p>
 */
public interface TenantReaderPort {

    Optional<TenantDetailGottenView> findDetailById(String tenantId);

    /**
     * 萬能多條件分頁查詢
     * <p>
     * 參數降級為基本型別，由底層 Adapter 去轉譯為框架專屬的分頁物件
     * </p>
     */
    PagedQueriedView<TenantSummarySearchedView> searchSummariesByFilters(
            String companyName,
            PlanType planType,
            TenantStatus status,
            int page,
            int size
    );
}