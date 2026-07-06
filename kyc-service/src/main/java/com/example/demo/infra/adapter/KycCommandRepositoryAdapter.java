package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.user.aggregate.UserIdentity;
import com.example.demo.application.port.KycCommandRepositoryPort;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.context.TenantContext;
import com.example.demo.infra.persistence.entity.UserIdentityEntity;
import com.example.demo.infra.persistence.repository.UserIdentityRepository;

import lombok.RequiredArgsConstructor;

/**
 * <h2>[基礎設施層 - 適配器] KYC 寫入側持久化適配器</h2>
 */
@Component
@RequiredArgsConstructor
public class KycCommandRepositoryAdapter implements KycCommandRepositoryPort {

	private final UserIdentityRepository jpaRepository;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	public Optional<UserIdentity> findById(String id) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndId(currentTenantId, id).map(UserIdentityEntity::toDomain);
	}

	@Override
	public void save(UserIdentity identity) {
	    // 1. 確保嚴格的租戶一致性
	    String currentTenantId = TenantContext.getCurrentTenantId();
	    if (!currentTenantId.equals(identity.getTenantId())) {
	        throw new SecurityException("Tenant Context mismatch! Threat detected.");
	    }

	    // 2. 儲存或更新實體
	    Optional<UserIdentityEntity> existingEntity = jpaRepository.findByTenantIdAndId(currentTenantId, identity.getId());
	    
	    if (existingEntity.isPresent()) {
	        // 更新情境：實體已存在，僅透過公開的行為方法覆寫狀態
	        UserIdentityEntity dbEntity = existingEntity.get();
	        dbEntity.updateFromDomain(identity);
	        jpaRepository.save(dbEntity);
	    } else {
	        // 核心修正：新增情境，直接呼叫實體的靜態工廠方法，不再依賴無參數建構子與 Setter
	        UserIdentityEntity newEntity = UserIdentityEntity.fromDomain(identity);
	        jpaRepository.save(newEntity);
	    }

	    // 3. 提取領域事件 (此事件不包含任何明文 PII)
	    List<DomainEvent> domainEvents = identity.pullDomainEvents();

	    // 4. 將事件裝入信封並拋轉給 Spring 總線 (由 Outbox 攔截落盤並送往 Kafka)
	    for (DomainEvent domainEvent : domainEvents) {
	        eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, domainEvent));
	    }
	}
}