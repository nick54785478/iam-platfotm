package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.domain.user.aggregate.vo.UserId;
import com.example.demo.application.port.UserWriterPort;
import com.example.demo.application.shared.event.DomainEvent;
import com.example.demo.application.shared.event.TenantEventEnvelope;
import com.example.demo.infra.context.TenantContext;
import com.example.demo.infra.persistence.entity.user.UserDbEntity;
import com.example.demo.infra.persistence.repository.SpringDataUserRepository;

@Component
class UserWriterAdapter implements UserWriterPort {

	private final SpringDataUserRepository jpaRepository;
	private final ApplicationEventPublisher eventPublisher; // 👈 搬到這裡

	public UserWriterAdapter(SpringDataUserRepository jpaRepository, ApplicationEventPublisher eventPublisher) {
		this.jpaRepository = jpaRepository;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public Optional<User> findById(UserId id) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndId(currentTenantId, id.value()).map(UserDbEntity::toDomain);
	}

	@Override
	public Optional<User> findByUsername(String username) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndUsername(currentTenantId, username).map(UserDbEntity::toDomain);
	}

	@Override
	public void save(User user) {
		// 1. 獲取當前執行緒中絕對可靠的 TenantId
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 2. 儲存業務資料（與之前一致）
		Optional<UserDbEntity> existingEntity = jpaRepository.findByTenantIdAndId(currentTenantId,
				user.getId().value());
		if (existingEntity.isPresent()) {
			UserDbEntity dbEntity = existingEntity.get();
			dbEntity.updateFromDomain(user);
			jpaRepository.save(dbEntity);
		} else {
			UserDbEntity newEntity = UserDbEntity.fromDomain(user, currentTenantId);
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