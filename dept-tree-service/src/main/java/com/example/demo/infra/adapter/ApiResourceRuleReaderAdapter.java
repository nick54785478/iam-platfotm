package com.example.demo.infra.adapter;


import com.example.demo.application.port.ApiResourceRuleReaderPort;
import com.example.demo.application.shared.dto.PagedApiResourceRuleGottenResult;
import com.example.demo.application.shared.query.SearchApiResourceRuleQuery;
import com.example.demo.infra.apirule.ApiResourceRule;
import com.example.demo.infra.persistence.ApiResourceRulePersistence;
import com.example.demo.infra.spec.ApiResourceRuleSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import security.dto.ApiResourceRuleGottenResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <h2>[基礎設施層] API 規則讀取適配器</h2>
 */
@Component
@RequiredArgsConstructor
class ApiResourceRuleReaderAdapter implements ApiResourceRuleReaderPort {

    private final ApiResourceRulePersistence jpaRepository;

    @Override
    public List<ApiResourceRuleGottenResult> findAllActiveRulesSortedByPriority() {
        return jpaRepository.findByIsActiveTrueOrderByPriorityAsc()
                .stream()
                .map(ApiResourceRule::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<PagedApiResourceRuleGottenResult> findPagedRulesForAdmin(SearchApiResourceRuleQuery query, Pageable pageable) {
        // 呼叫 Specification 工廠組裝 WHERE 條件
        Specification<ApiResourceRule> spec = ApiResourceRuleSpecifications.withDynamicQuery(query);

        // 丟給 JPA 執行，並將結果映射為 View DTO
        return jpaRepository.findAll(spec, pageable)
                .map(ApiResourceRule::toAdminView);
    }


}