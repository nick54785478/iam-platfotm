package com.example.demo.infra.adapter;

import com.example.demo.application.port.ApiResourceRuleReaderPort;
import com.example.demo.application.shared.dto.ApiResourceRuleGottenResult;
import com.example.demo.infra.apirule.ApiResourceRule;
import com.example.demo.infra.persistence.ApiResourceRulePersistence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}