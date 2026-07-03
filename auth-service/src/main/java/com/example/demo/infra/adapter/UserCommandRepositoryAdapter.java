package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.domain.user.aggregate.vo.UserId;
import com.example.demo.application.port.UserCommandRepositoryPort;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.context.TenantContext;
import com.example.demo.infra.persistence.entity.user.UserEntity;
import com.example.demo.infra.persistence.repository.UserRepository;

@Component
class UserCommandRepositoryAdapter implements UserCommandRepositoryPort {

	private final UserRepository jpaRepository;
	private final ApplicationEventPublisher eventPublisher;

	public UserCommandRepositoryAdapter(UserRepository jpaRepository, ApplicationEventPublisher eventPublisher) {
		this.jpaRepository = jpaRepository;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public Optional<User> findById(UserId id) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndId(currentTenantId, id.value()).map(UserEntity::toDomain);
	}

	@Override
	public Optional<User> findByUsername(String username) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndUsername(currentTenantId, username).map(UserEntity::toDomain);
	}

	@Override
	public void save(User user) {
		// 1. 獲取當前執行緒中絕對可靠的 TenantId
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 2. 儲存業務資料（與之前一致）
		Optional<UserEntity> existingEntity = jpaRepository.findByTenantIdAndId(currentTenantId,
				user.getId().value());
		if (existingEntity.isPresent()) {
			UserEntity dbEntity = existingEntity.get();
			dbEntity.updateFromDomain(user);
			jpaRepository.save(dbEntity);
		} else {
			UserEntity newEntity = UserEntity.fromDomain(user, currentTenantId);
			jpaRepository.save(newEntity);
		}

		// 3. 提取純潔的、沒有租戶污染的領域事件
		List<DomainEvent> domainEvents = user.pullDomainEvents();

		// 4. 魔法發生處：將領域事件裝入「租戶信封」，然後 Publish
		for (DomainEvent domainEvent : domainEvents) {
			eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, domainEvent));
		}
	}
}