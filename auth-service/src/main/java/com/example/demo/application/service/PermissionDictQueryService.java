package com.example.demo.application.service;

import com.example.demo.application.port.PermissionDictReaderPort;
import com.example.demo.application.shared.dto.PermissionDictGottenResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class PermissionDictQueryService {

    private final PermissionDictReaderPort permissionDictReader;

    public List<PermissionDictGottenResult> searchPermissions(String tenantId,String module,String keyword) {

        String safeModule = StringUtils.hasText(module) ? module : null;
        String safeKeyword = StringUtils.hasText(keyword) ? keyword : null;
        log.info("[CQRS-Query] 執行權限字典查詢. Tenant: {}, Module: {}, Keyword: {}",
                tenantId, safeModule, safeKeyword);

        return permissionDictReader.searchPermissions(tenantId, safeModule, safeKeyword);
    }
}
