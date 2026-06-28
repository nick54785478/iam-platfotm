package com.example.demo.infra.adapter;

import com.example.demo.infra.apirule.entity.ApiResourceRule;
import com.example.demo.infra.apirule.repository.ApiResourceRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import security.dto.ApiResourceRuleGottenResult;
import security.port.ApiResourceRuleQueryRepositoryPort;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
class ApiResourceRuleQueryRepositoryAdapter implements ApiResourceRuleQueryRepositoryPort {

    private final ApiResourceRuleRepository repository;

    /**
     * [新增實作] 撈取租戶專屬與系統預設規則 (Tenant Override Pattern)
     */
    @Override
    public List<ApiResourceRuleGottenResult> findRulesForTenantAndSystem(String tenantId, String systemTenant) {

        log.info("tenantId: {}, systemTenant:{}", tenantId, systemTenant);
        // 將當前租戶與系統租戶打包為條件集合
        // 💡 巧思：如果當前租戶剛好就是 SYSTEM，List.of 會有重複元素，
        // 但 JPA 底層轉為 SQL 的 IN ('SYSTEM', 'SYSTEM') 並不影響效能與正確性，
        // 若追求極致純潔，也可改用 Set.of(tenantId, systemTenant) 來自動去重。
        List<String> targetTenants = Stream.of(tenantId, systemTenant)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // 交給 JPA 執行，並轉譯為內圈需要的唯讀 DTO
        return repository.findByTenantIdInAndIsActiveTrueOrderByPriorityAsc(targetTenants)
                .stream()
                .map(ApiResourceRule::toDomain)
                .collect(Collectors.toList());
    }
}
