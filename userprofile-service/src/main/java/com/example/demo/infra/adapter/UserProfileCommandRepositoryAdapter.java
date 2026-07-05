package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.user.aggregate.UserProfile;
import com.example.demo.application.port.UserProfileCommandRepositoryPort;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.context.TenantContext;
import com.example.demo.infra.persistence.entity.UserProfileEntity;
import com.example.demo.infra.persistence.repository.UserProfileRepository;

/**
 * <h2>[基礎設施層 - 適配器] 個人檔案寫入側持久化適配器</h2>
 */
@Component
class UserProfileCommandRepositoryAdapter implements UserProfileCommandRepositoryPort {

	private final UserProfileRepository jpaRepository;
	private final ApplicationEventPublisher eventPublisher;

	public UserProfileCommandRepositoryAdapter(UserProfileRepository jpaRepository,
			ApplicationEventPublisher eventPublisher) {
		this.jpaRepository = jpaRepository;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public Optional<UserProfile> findById(String id) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndId(currentTenantId, id).map(UserProfileEntity::toDomain);
	}

	@Override
	public void save(UserProfile profile) {
		// 1. 獲取當前執行緒中絕對可靠的 TenantId
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 2. 儲存業務資料
		Optional<UserProfileEntity> existingEntity = jpaRepository.findByTenantIdAndId(currentTenantId,
				profile.getId());
		if (existingEntity.isPresent()) {
			UserProfileEntity dbEntity = existingEntity.get();
			dbEntity.updateFromDomain(profile);
			jpaRepository.save(dbEntity);
		} else {
			UserProfileEntity newEntity = UserProfileEntity.fromDomain(profile, currentTenantId);
			jpaRepository.save(newEntity);
		}

		// 3. 提取純潔的、沒有租戶污染的領域事件
		List<DomainEvent> domainEvents = profile.pullDomainEvents();

		// 4. 將領域事件裝入「租戶信封」，交由 Spring 事件總線發佈 (觸發 Outbox 落盤)
		for (DomainEvent domainEvent : domainEvents) {
			eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, domainEvent));
		}
	}
}