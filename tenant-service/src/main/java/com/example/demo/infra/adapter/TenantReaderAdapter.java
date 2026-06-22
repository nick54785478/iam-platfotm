package com.example.demo.infra.adapter;

import com.example.demo.application.domain.tenant.aggregate.vo.PlanType;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantStatus;
import com.example.demo.application.port.TenantReaderPort;
import com.example.demo.application.shared.dto.PagedQueriedView;
import com.example.demo.application.shared.dto.TenantDetailGottenView;
import com.example.demo.application.shared.dto.TenantSummarySearchedView;
import com.example.demo.infra.persistence.repository.SpringDataTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantReaderAdapter implements TenantReaderPort {

    private final SpringDataTenantRepository jpaQueryRepository;

    @Override
    public Optional<TenantDetailGottenView> findDetailById(String tenantId) {
        return jpaQueryRepository.findByTenantId(tenantId, TenantDetailGottenView.class);
    }

    @Override
    public PagedQueriedView<TenantSummarySearchedView> searchSummariesByFilters(
            String companyName,
            PlanType planType,
            TenantStatus status,
            int page,
            int size) {

        // 1. 框架依賴隔離：在這裡動態建立 Spring 的 Pageable
        Pageable pageable = PageRequest.of(page, size, Sort.by("tenantId").descending());

        // 2. 執行底層查詢
        Page<TenantSummarySearchedView> springPage = jpaQueryRepository.findTenantsByFilters(
                companyName, planType, status, pageable
        );

        // 3. 框架依賴隔離：將 Spring 的 Page 轉換為你的標準 DTO
        return PagedQueriedView.<TenantSummarySearchedView>builder()
                .content(springPage.getContent())
                .totalElements(springPage.getTotalElements())
                .page(springPage.getNumber())
                .size(springPage.getSize())
                .build();
    }
}