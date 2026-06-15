package com.example.demo.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.GroupReaderPort;
import com.example.demo.application.shared.dto.GroupRepresentation;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[應用層 - 服務] 群組 CQRS 讀取編排服務 (Group Query Service) - 終極純潔版</h2>
 */
@Service
@Transactional(readOnly = true)
public class GroupQueryService {

	private final GroupReaderPort groupReaderPort;

	public GroupQueryService(GroupReaderPort groupReaderPort) {
		this.groupReaderPort = groupReaderPort;
	}

	public GroupRepresentation getGroupByCode(String groupCode) {
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 🚀 舒適！內圈大腦此時不需要知道任何關於 CSV 拆解或 JPA 的技術細節，直接收獲完全體 DTO
		return groupReaderPort.fetchByGroupCode(currentTenantId, groupCode).orElseThrow(
				() -> new IllegalArgumentException("Group code '" + groupCode + "' not found in current tenant"));
	}

	public List<GroupRepresentation> getAllGroupsOfCurrentTenant() {
		String currentTenantId = TenantContext.getCurrentTenantId();
		return groupReaderPort.fetchAllByTenant(currentTenantId);
	}
}