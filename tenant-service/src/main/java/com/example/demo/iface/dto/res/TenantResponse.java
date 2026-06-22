package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.PagedQueriedView;
import com.example.demo.application.shared.dto.TenantDetailGottenView;
import com.example.demo.application.shared.dto.TenantSummarySearchedView;

public class TenantResponse {

    /**
     * 回傳剛建立的租戶 ID
     */
    public record TenantProvisionedResource(
            String code,
            String message,
            String tenantId
    ) {}

    public record TenantDetailGottenResource(
            String code,
            String message,
            TenantDetailGottenView data
    ) {}

    public record TenantSummarySearchedResource(
            String code,
            String message,
            PagedQueriedView<TenantSummarySearchedView> data
    ) {}

    public record TenantSuspendedResource(
            String code,
            String message) {}

    public record TenantUpgradedResource(
            String code,
            String message) {}

}
