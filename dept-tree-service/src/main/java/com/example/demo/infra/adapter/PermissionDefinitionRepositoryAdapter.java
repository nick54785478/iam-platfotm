package com.example.demo.infra.adapter;


import com.example.demo.application.domain.permission.aggregate.PermissionDefinition;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionCode;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionId;
import com.example.demo.application.domain.permission.repository.PermissionDefinitionRepository;
import com.example.demo.application.domain.shared.vo.TenantId;
import com.example.demo.infra.persistence.PermissionDefinitionPersistence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <h2>[基礎設施層] 權限定義倉儲適配器 (Driven Adapter)</h2>
 * <p>
 * 扮演領域層與底層 Spring Data JPA 之間的橋樑。
 * </p>
 */
@Component
@RequiredArgsConstructor
class PermissionDefinitionRepositoryAdapter implements PermissionDefinitionRepository {

    private final PermissionDefinitionPersistence persistence;

    @Override
    public Optional<PermissionDefinition> findById(TenantId tenantId, PermissionId id) {
        return persistence.findByTenantIdAndId(tenantId, id);
    }

    @Override
    public Optional<PermissionDefinition> findByTenantIdAndCode(TenantId tenantId, PermissionCode code) {
        return persistence.findByTenantIdAndCode(tenantId, code);
    }

    @Override
    public boolean existsByTenantIdAndCode(TenantId tenantId, PermissionCode code) {
        return persistence.existsByTenantIdAndCode(tenantId, code);
    }

    @Override
    public PermissionDefinition save(PermissionDefinition permissionDefinition) {
        // 💡 框架魔法：當這裡執行 save 時，Spring Data JPA 會自動攔截 @DomainEvents 並廣播事件
        return persistence.save(permissionDefinition);
    }
}