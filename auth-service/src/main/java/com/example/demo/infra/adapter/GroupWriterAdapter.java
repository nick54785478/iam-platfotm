package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.domain.group.aggregate.Group;
import com.example.demo.application.domain.group.aggregate.vo.GroupId;
import com.example.demo.application.domain.user.aggregate.vo.UserId;
import com.example.demo.application.port.GroupWriterPort;
import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.context.TenantContext;
import com.example.demo.infra.persistence.entity.group.GroupEntity;
import com.example.demo.infra.persistence.repository.GroupRepository;

/**
 * <h2>[基礎設施層 - 適配器] 群組寫入側持久化適配器 (Group Writer Adapter) - 完全體</h2>
 */
@Component
public class GroupWriterAdapter implements GroupWriterPort {

    private final GroupRepository groupRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GroupWriterAdapter(GroupRepository groupRepository, ApplicationEventPublisher eventPublisher) {
        this.groupRepository = groupRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Group> findById(GroupId id) {
        String currentTenantId = TenantContext.getCurrentTenantId();
        return groupRepository.findByTenantIdAndId(currentTenantId, id.value())
                .map(GroupEntity::toDomain);
    }

    @Override
    public Optional<Group> findByGroupCode(String groupCode) {
        String currentTenantId = TenantContext.getCurrentTenantId();
        return groupRepository.findByTenantIdAndGroupCode(currentTenantId, groupCode)
                .map(GroupEntity::toDomain);
    }

    /**
     * <b>🚀 補齊寫入側規格：依據使用者物理 ID，撈出該同仁參與的所有群組模型</b>
     * <p><b>【技術細節】</b>：底層對應的是 {@code @ElementCollection} 展開的關係表查詢。<br>
     * 需確保 SpringDataGroupRepository 宣告有：<br>
     * {@code List<GroupDbEntity> findByTenantIdAndMemberUserIdsContaining(String tenantId, UUID userId);}</p>
     */
    @Override
    public List<Group> findGroupsByUserId(UserId userId) {
        String currentTenantId = TenantContext.getCurrentTenantId();
        
        // 透過 JPA 內嵌集合查詢，精準阻斷跨租戶漏洞，並將結果還原 (Rehydration) 為領域充血模型
        return groupRepository.findByTenantIdAndMemberUserIdsContaining(currentTenantId, userId.value())
        		.stream()
        		.map(GroupEntity::toDomain)
        		.toList();
    }

    @Override
    public void save(Group group) {
        String currentTenantId = TenantContext.getCurrentTenantId();
        Optional<GroupEntity> existingEntityOpt = groupRepository.findByTenantIdAndId(currentTenantId, group.getId().value());

        if (existingEntityOpt.isPresent()) {
            GroupEntity existingEntity = existingEntityOpt.get();
            existingEntity.updateFromDomain(group);
            groupRepository.save(existingEntity);
        } else {
            GroupEntity newEntity = GroupEntity.fromDomain(group, currentTenantId);
            groupRepository.save(newEntity);
        }

        List<DomainEvent> domainEvents = group.pullDomainEvents();
        for (DomainEvent event : domainEvents) {
            eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, event));
        }
    }

    @Override
    public void delete(Group group) {
        String currentTenantId = TenantContext.getCurrentTenantId();
        groupRepository.findByTenantIdAndId(currentTenantId, group.getId().value())
                .ifPresent(groupRepository::delete);
    }
}