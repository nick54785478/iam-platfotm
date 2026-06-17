package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.demo.application.port.GroupReaderPort;
import com.example.demo.application.shared.dto.GroupRepresentation;
import com.example.demo.infra.projection.repository.GroupViewRepository;
import com.example.demo.infra.projection.view.GroupView;

/**
 * <h2>[基礎設施層 - 適配器] 群組讀取側持久化適配器 (Group Reader Adapter)</h2>
 * <p>
 * <b>【六角形美感】</b>：作為防腐層，在內部將技術模型 (GroupView) 徹底攔截並轉譯，絕不讓 JPA 污染內圈。
 * </p>
 */
@Component
class GroupReaderAdapter implements GroupReaderPort {

	private final GroupViewRepository groupViewRepository;

	public GroupReaderAdapter(GroupViewRepository groupViewRepository) {
        this.groupViewRepository = groupViewRepository;
    }

	@Override
	public Optional<GroupRepresentation> fetchByGroupCode(String tenantId, String groupCode) {
		return groupViewRepository.findByTenantIdAndGroupCode(tenantId, groupCode).map(this::toRepresentation); // 🚀
																												// 在外圈當場蒸發掉
																												// GroupView
																												// 實體
	}

	@Override
	public List<GroupRepresentation> fetchAllByTenant(String tenantId) {
		return groupViewRepository.findByTenantId(tenantId).stream().map(this::toRepresentation).toList();
	}

	/**
	 * 內聚的轉譯工具方法 (Mapping)
	 */
	private GroupRepresentation toRepresentation(GroupView view) {
		return new GroupRepresentation(view.getId(), view.getGroupName(), view.getGroupCode(),
				view.getMemberUserIdsAsSet(), // 內聚解開 CSV
				view.getAssignedRoleIdsAsSet() // 內聚解開 CSV
		);
	}
}