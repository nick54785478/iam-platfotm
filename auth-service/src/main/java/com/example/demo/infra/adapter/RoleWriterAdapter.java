package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.port.RoleWriterPort;
import com.example.demo.application.shared.event.DomainEvent;
import com.example.demo.application.shared.event.TenantEventEnvelope;
import com.example.demo.infra.context.TenantContext;
import com.example.demo.infra.persistence.entity.role.RoleDbEntity;
import com.example.demo.infra.persistence.repository.SpringDataRoleRepository;

/**
 * <h2>[基礎設施層 - 適配器] 角色寫入側持久化適配器 (Role Writer Adapter) - 完全體</h2>
 */
@Component
public class RoleWriterAdapter implements RoleWriterPort {

	private final SpringDataRoleRepository jpaRepository;
	private final ApplicationEventPublisher eventPublisher;

	public RoleWriterAdapter(SpringDataRoleRepository jpaRepository, ApplicationEventPublisher eventPublisher) {
		this.jpaRepository = jpaRepository;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public Optional<Role> findById(RoleId id) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndId(currentTenantId, id.value()).map(RoleDbEntity::toDomain);
	}

	@Override
	public Optional<Role> findByRoleCode(String roleCode) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return jpaRepository.findByTenantIdAndRoleCode(currentTenantId, roleCode).map(RoleDbEntity::toDomain);
	}

	/**
	 * <b>🚀 補齊寫入側規格：將一整群物理 RoleId 批次還原，並提取出人類可讀的角色代碼 (roleCode)</b>
	 * <p>
	 * 需確保 SpringDataRoleRepository 宣告有：<br>
	 * {@code List<RoleDbEntity> findByTenantIdAndIdIn(String tenantId, Collection<UUID> ids);}
	 * </p>
	 */
	@Override
	public Set<String> findRoleCodesByRoleIds(Set<RoleId> roleIds) {
		if (roleIds == null || roleIds.isEmpty()) {
			return Set.of();
		}
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 拆開 RoleId 包裝，提取原始 UUID 集合
		Set<UUID> uuids = roleIds.stream().map(RoleId::value).collect(Collectors.toSet());

		// 批次查詢並抽取出 roleCode
		return jpaRepository.findByTenantIdAndIdIn(currentTenantId, uuids).stream().map(RoleDbEntity::getRoleCode)
				.collect(Collectors.toSet());
	}

	/**
	 * <b>🚀 補齊寫入側規格：傳入一整群角色代碼，去關係表聯集出最終扁平化的「系統權限點」字串集合</b>
	 * <p>
	 * <b>【效能優化】</b>：直接直擊一對多關係表，利用 Stream
	 * 摺疊展開。回傳格式如：{@code ["order-service:ORDER_VIEW"]}
	 * </p>
	 * <p>
	 * 需確保 SpringDataRoleRepository 宣告有：<br>
	 * {@code List<RoleDbEntity> findByTenantIdAndRoleCodeIn(String tenantId, Collection<String> roleCodes);}
	 * </p>
	 */
	@Override
	public Set<String> findPermissionStringsByRoleCodes(Set<String> roleCodes) {
		if (roleCodes == null || roleCodes.isEmpty()) {
			return Set.of();
		}
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 批次拉出這群角色實體，並在記憶體內拼裝出 "system_code:permission_code" 的標準鑑權契約字串
		return jpaRepository.findByTenantIdAndRoleCodeIn(currentTenantId, roleCodes).stream()
				.flatMap(roleEntity -> roleEntity.getPermissions().stream()) // 展開一對多權限集合
				.map(p -> p.getSystemCode() + ":" + p.getPermissionCode()) // 格式化
				.collect(Collectors.toSet()); // 自動去重 (Union)
	}

	@Override
	public void save(Role role) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		Optional<RoleDbEntity> existingEntity = jpaRepository.findByTenantIdAndId(currentTenantId,
				role.getId().value());
		if (existingEntity.isPresent()) {
			RoleDbEntity dbEntity = existingEntity.get();
			dbEntity.updateFromDomain(role);
			jpaRepository.save(dbEntity);
		} else {
			RoleDbEntity newEntity = RoleDbEntity.fromDomain(role, currentTenantId);
			jpaRepository.save(newEntity);
		}

		List<DomainEvent> domainEvents = role.pullDomainEvents();
		for (DomainEvent domainEvent : domainEvents) {
			eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, domainEvent));
		}
	}

	@Override
	public void delete(Role role) {
		String currentTenantId = TenantContext.getCurrentTenantId();
		jpaRepository.findByTenantIdAndId(currentTenantId, role.getId().value()).ifPresent(jpaRepository::delete);

		List<DomainEvent> domainEvents = role.pullDomainEvents();
		for (DomainEvent domainEvent : domainEvents) {
			eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, domainEvent));
		}
	}
}