package com.example.demo.infra.adapter;


import com.example.demo.application.port.ApiResourceRuleWriterPort;
import com.example.demo.infra.apirule.ApiResourceRule;
import com.example.demo.infra.persistence.ApiResourceRulePersistence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ApiResourceRuleWriterAdapter implements ApiResourceRuleWriterPort {

    private final ApiResourceRulePersistence jpaRepository;

    @Override
    public Optional<ApiResourceRule> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void save(ApiResourceRule rule) {
        jpaRepository.save(rule);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}