package com.example.demo.infra.adapter;

import com.example.demo.application.domain.tenant.aggregate.Tenant;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantId;
import com.example.demo.application.port.TenantWriterPort;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.persistence.entity.TenantEntity;
import com.example.demo.infra.persistence.repository.SpringDataTenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * <h2>[基礎設施層 - 適配器] 租戶寫入側持久化適配器 (Tenant Writer Adapter)</h2>
 * <p>
 * 實作 {@link TenantWriterPort} 契約。負責處理 JPA 物理儲存，
 * 並將聚合根內聚產生的 DomainEvent 自動包裝進全域多租戶信封中發射，完美解耦技術細節。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class TenantWriterAdapter implements TenantWriterPort {

    private final SpringDataTenantRepository jpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jpaRepository.findById(id.value())
                .map(TenantEntity::toDomain);
    }

    @Override
    public void save(Tenant tenant) {
        // 1. 執行關係型資料庫物理持久化 (Upsert 語意)
        Optional<TenantEntity> existingEntity = jpaRepository.findById(tenant.getId().value());

        if (existingEntity.isPresent()) {
            TenantEntity dbEntity = existingEntity.get();
            dbEntity.updateFromDomain(tenant);
            jpaRepository.save(dbEntity);
        } else {
            TenantEntity newEntity = TenantEntity.fromDomain(tenant);
            jpaRepository.save(newEntity);
        }

        // 2. 🛡️ 提取純潔的、沒有技術污染的內部領域事件
        List<DomainEvent> domainEvents = tenant.pullDomainEvents();

        // 3. 核心共舞：將領域事件裝入「租戶信封」並發射
        // 對於 TenantService 而言，當前的租戶信封標識就是該 Tenant 自身的 ID
        String currentTenantId = tenant.getId().value();

        for (DomainEvent domainEvent : domainEvents) {
            eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, domainEvent));
        }
    }
}