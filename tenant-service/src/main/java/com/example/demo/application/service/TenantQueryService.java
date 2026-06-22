package com.example.demo.application.service;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;
import com.example.demo.application.port.TenantReaderPort;
import com.example.demo.application.shared.dto.PagedQueriedView;
import com.example.demo.application.shared.dto.TenantDetailGottenView;
import com.example.demo.application.shared.dto.TenantSummarySearchedView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantQueryService {

    private final TenantReaderPort queryPort;

    public TenantDetailGottenView getTenantDetail(String tenantId) {
        return queryPort.findDetailById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("租戶不存在: " + tenantId));
    }

    public PagedQueriedView<TenantSummarySearchedView> searchTenants(
            String companyName,
            PlanType planType,
            TenantStatus status,
            int page,
            int size) {

        // 處理空白字串防呆
        String filterName = (companyName != null && !companyName.isBlank()) ? companyName : null;

        // 直接傳遞基本參數，應用層完全感受不到 Spring Data 的存在
        return queryPort.searchSummariesByFilters(filterName, planType, status, page, size);
    }
}