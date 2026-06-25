package com.example.demo.infra.spec;


import com.example.demo.application.shared.query.SearchApiResourceRuleQuery;
import com.example.demo.infra.apirule.ApiResourceRule;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * <h2>[基礎設施層] API 規則動態查詢條件構造器</h2>
 */
public class ApiResourceRuleSpecifications {

    public static Specification<ApiResourceRule> withDynamicQuery(SearchApiResourceRuleQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Tenant (EQUAL)
            if (query.tenantId() != null && !query.tenantId().isBlank()) {
                predicates.add(cb.equal(root.get("tenantId"), query.tenantId()));
            }

            // 2. HTTP Method (EQUAL) - 自動轉大寫防呆
            if (query.httpMethod() != null && !query.httpMethod().isBlank()) {
                predicates.add(cb.equal(root.get("httpMethod"), query.httpMethod().toUpperCase()));
            }

            // 3. Path Pattern (LIKE) - 模糊匹配前後加上 %
            if (query.pathPattern() != null && !query.pathPattern().isBlank()) {
                predicates.add(cb.like(root.get("pathPattern"), "%" + query.pathPattern() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}