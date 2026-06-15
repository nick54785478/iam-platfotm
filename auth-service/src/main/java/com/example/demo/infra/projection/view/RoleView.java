package com.example.demo.infra.projection.view;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * <h2>[基礎設施層 - 投影視圖] 角色與權限極速讀取實體 (Role View)</h2>
 * <p><b>【設計天職】</b>：<br>
 * 本實體專為 CQRS 讀取側（Query Side）服務。它打破了傳統關係型資料庫一對多的設計，
 * 透過<b>去正規化（Denormalized）</b>將該角色的所有權限摺疊成一個大 JSON 欄位（{@code permissions_json}）。
 * 前端或網關拉取權限時，無需執行任何 SQL {@code JOIN}，一發索引查詢即可秒殺帶走所有權限點。</p>
 * <p><b>【技術聯防】</b>：<br>
 * 1. <b>表名語意索引</b>：將索引明確命名為 {@code idx_role_view_tenant_code}，完美錯開與寫入實體表的名稱衝突。<br>
 * 2. <b>唯讀安全封裝</b>：本表不設任何狀態變更業務方法，全由背景的 ProjectionProcessor 異步重寫。</p>
 */
@Entity
@Table(name = "role_view", indexes = {
		// 🚀 頂規優化：建立租戶內的 role_code 複合唯一索引，確保外圈以 roleCode 查詢時速度飛快
		@Index(name = "idx_role_view_tenant_code", columnList = "tenant_id, role_code", unique = true) })
public class RoleView {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id; // 🟢 物理主鍵依然保持 UUID，守住與寫入側聚合根的一致性錨點

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	@Column(name = "role_name", nullable = false)
	private String roleName;

	@Column(name = "role_code", nullable = false, updatable = false)
	private String roleCode; // 🚀 API 查詢的主角業務鍵

	@Column(name = "is_system_root", nullable = false)
	private boolean isSystemRoot;

	@Lob
	@Column(name = "permissions_json", nullable = false, columnDefinition = "TEXT")
	private String permissionsJson; // 🚀 極致去正規化：將該角色的權限點清單直接打成扁平 JSON 儲存

	/** JPA 要求的無參建構式 */
	protected RoleView() {
	}

	/** 全參建構式：供背景 Projection 投影處理器全新建立或覆蓋快照時調用 */
	public RoleView(UUID id, String tenantId, String roleName, String roleCode, boolean isSystemRoot,
			String permissionsJson) {
		this.id = id;
		this.tenantId = tenantId;
		this.roleName = roleName;
		this.roleCode = roleCode;
		this.isSystemRoot = isSystemRoot;
		this.permissionsJson = permissionsJson;
	}

	// ── 唯讀 Getters ──
	public UUID getId() {
		return id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getRoleName() {
		return roleName;
	}

	public String getRoleCode() {
		return roleCode;
	}

	public boolean isSystemRoot() {
		return isSystemRoot;
	}

	public String getPermissionsJson() {
		return permissionsJson;
	}
}