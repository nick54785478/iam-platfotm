package com.example.demo.infra.persistence;

import com.example.demo.infra.apirule.ApiResourceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApiResourceRulePersistence extends JpaRepository<ApiResourceRule, Long> {

    /**
     * 撈出所有啟用的規則，並依照 Priority 升冪排序 (數字越小越優先)
     */
    List<ApiResourceRule> findByIsActiveTrueOrderByPriorityAsc();
}