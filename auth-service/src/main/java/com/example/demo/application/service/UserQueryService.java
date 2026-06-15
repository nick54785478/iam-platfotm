package com.example.demo.application.service;


import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.UserReaderPort;
import com.example.demo.application.shared.dto.UserRepresentation;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[應用層 - 服務] 使用者 CQRS 讀取編排服務 (User Query Service)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別完全為讀取側（Query Side）服務，完全與寫入側的 {@code User} 充血模型解耦。 它直接面向前端或網關的呈現規格，透過
 * ReaderPort 一發直擊極致扁平化、去正規化後的 {@code user_view} 投影快照表。
 * </p>
 * <p>
 * <b>【技術亮點】</b>：<br>
 * 1. <b>效能突破 (JPA Optimization)</b>：全類別標註
 * {@code @Transactional(readOnly = true)}。 這會硬核通知 Hibernate 徹底關閉「快照髒檢查（Flush
 * Dirty Checking）」，免除所有記憶體副本開銷，查詢速度提升數倍。<br>
 * 2. <b>免去多表 JOIN (CSV 摺疊技術)</b>：所查詢的視圖中，角色已被摺疊為 CSV 欄位（如 "ADMIN,USER"），一發 SQL
 * 秒殺回傳，極致優化高併發效能。
 * </p>
 */
@Service
@Transactional(readOnly = true) // 🚀 唯讀事務調優，關閉髒檢查，記憶體零拷貝開銷
public class UserQueryService {

	private final UserReaderPort userReaderPort;

	public UserQueryService(UserReaderPort userReaderPort) {
		this.userReaderPort = userReaderPort;
	}

	/**
	 * <b>依據 username 查詢單一用戶詳細視圖快照</b>
	 * <p>
	 * 安全防禦：自動從上下文安全拔出當前登入者的租戶 ID，搭配 username 進行複合唯一索引精準打擊，$O(1)$ 複雜度回傳，防禦租戶越權。
	 * </p>
	 */
	public UserRepresentation getUserByUsername(String username) {
		// 1. 安全地從 ThreadLocal 提取當前安全隔離的租戶 ID
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 2. 傳入複合雙主角，直擊投影快照表
		return userReaderPort.fetchByUsername(currentTenantId, username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found in current tenant"));
	}

	/**
	 * <b>查詢當前租戶下的全量使用者視圖清單 (Read List)</b>
	 */
	public List<UserRepresentation> getAllUsersOfCurrentTenant() {
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 直擊以 tenant_id 為單一索引的 user_view 投影表
		return userReaderPort.fetchAllByTenant(currentTenantId);
	}
}