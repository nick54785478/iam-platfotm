package com.example.demo.infra.apirule.repository;

import com.example.demo.infra.apirule.entity.ApiResourceRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiResourceRuleRepository extends JpaRepository<ApiResourceRule, Long> {

    Optional<ApiResourceRule> findByIdAndTenantId(Long id, String tenantId);

    /**
     * 撈出所有啟用的規則，並依照 Priority 升冪排序 (數字越小越優先)
     */
    List<ApiResourceRule> findByIsActiveTrueOrderByPriorityAsc();

    /**
     * 撈出指定租戶清單 (通常是 Current Tenant + SYSTEM) 內的所有啟用規則，並依優先級排序
     *
     * @param tenantIds 租戶 ID 集合 (例如: List.of("VIP_WITS", "SYSTEM"))
     */
    List<ApiResourceRule> findByTenantIdInAndIsActiveTrueOrderByPriorityAsc(Collection<String> tenantIds);

    Page<ApiResourceRule> findAll(Specification<ApiResourceRule> specification, Pageable pageable);
}