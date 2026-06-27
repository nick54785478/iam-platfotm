package com.example.demo.infra.adapter;


import com.example.demo.application.port.PermissionDictReaderPort;
import com.example.demo.application.shared.dto.PermissionDictGottenResult;
import com.example.demo.infra.projection.repository.PermissionDictViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h2>[基礎設施層] 權限字典讀取適配器 (CQRS Query Adapter)</h2>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionDictReaderAdapter implements PermissionDictReaderPort {

    private final PermissionDictViewRepository repository;

    @Override
    @Transactional(readOnly = true) // 🌟 唯讀交易優化，不產生 Hibernate Dirty Checking 開銷
    public List<PermissionDictGottenResult> searchPermissions(String tenantId, String module, String keyword) {

        log.debug("[CQRS-Query] 執行權限字典查詢. Tenant: {}, Module: {}, Keyword: {}",
                tenantId, module, keyword);

        return repository.searchByCriteria(tenantId, module, keyword)
                .stream()
                .map(po -> new PermissionDictGottenResult(
                        po.getId(),
                        po.getCode(),
                        po.getName(),
                        po.getDescription(),
                        po.getModule()
                ))
                .toList();
    }
}