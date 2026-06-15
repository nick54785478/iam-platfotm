package com.example.demo.application.service;


import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.RoleReaderPort;
import com.example.demo.application.shared.dto.RoleRepresentation;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[應用層 - 服務] 角色與權限 CQRS 讀取編排服務 (Role Query Service)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別負責角色與權限視圖的查詢調度。與傳統關係型資料庫要去 {@code roles} 串聯 {@code roles_permissions} 關聯表、
 * 產生兩三次大 JOIN 導致資料庫 CPU 飆高不同，本服務直接直擊擁有 JSON 摺疊欄位（{@code permissions_json}）的
 * {@code role_view} 投影表。
 * </p>
 */
@Service
@Transactional(readOnly = true) // 🚀 關閉快照髒檢查，極致優化 Query 唯讀性能
public class RoleQueryService {

	private final RoleReaderPort roleReaderPort;

	public RoleQueryService(RoleReaderPort roleReaderPort) {
		this.roleReaderPort = roleReaderPort;
	}

	/**
	 * <b>依據 roleCode 查詢單一角色詳細與全量權限清單</b>
	 * <p>
	 * 對齊以業務主角 roleCode 為主的路徑規格，底層轟擊複合唯一索引，秒速反序列化 JSON 回傳。
	 * </p>
	 */
	public RoleRepresentation getRoleByCode(String roleCode) {
		String currentTenantId = TenantContext.getCurrentTenantId();

		return roleReaderPort.fetchByRoleCode(currentTenantId, roleCode).orElseThrow(
				() -> new IllegalArgumentException("Role code '" + roleCode + "' not found in current tenant"));
	}

	/**
	 * <b>獲取當前租戶下的全量角色快照列表</b>
	 */
	public List<RoleRepresentation> getAllRolesOfCurrentTenant() {
		String currentTenantId = TenantContext.getCurrentTenantId();

		return roleReaderPort.fetchAllByTenant(currentTenantId);
	}
}