package com.example.demo.application.port;

import com.example.demo.infra.apirule.ApiResourceRule;

import java.util.Optional;

/**
 * <h2>[應用層 - 輸出埠] API 規則庫寫入合約</h2>
 */
public interface ApiResourceRuleWriterPort {

    Optional<ApiResourceRule> findById(Long id);

    void save(ApiResourceRule rule);

    void deleteById(Long id);
}