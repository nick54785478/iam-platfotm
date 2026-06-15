package com.example.demo.infra.projection.view;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * <h2>[基礎設施層 - 投影視圖] 使用者資訊與角色極速讀取實體 (User View)</h2>
 * <p><b>【設計天職】</b>：<br>
 * 本實體為 CQRS 讀取側的使用者快照。其精髓在於將使用者原本複雜的 {@code user_roles} 關聯表關係，
 * 壓縮並摺疊成單一欄位、以逗號分隔的 <b>CSV 字串（{@code roles_csv}，例如 "ADMIN,SALES"）</b>。
 * 徹底消滅後台管理介面在分頁查詢（Pagination List）時，因一對多關係所引發的經典 {@code N+1} 查詢災難。</p>
 */
@Entity
@Table(name = "user_view", indexes = { @Index(name = "idx_user_view_tenant_id_id", columnList = "tenant_id, id"),
		@Index(name = "idx_user_view_tenant_username", columnList = "tenant_id, username") // 🚀 供極速單筆查詢使用
})
public class UserView {

	@Id
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	@Column(name = "username", nullable = false)
	private String username;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "roles_csv")
	private String rolesCsv; // 🚀 CQRS 摺疊技術：將角色拉平成字串，如 "ADMIN,USER"

	protected UserView() {
	}

	public UserView(UUID id, String tenantId, String username, String email, String status, String rolesCsv) {
		this.id = id;
		this.tenantId = tenantId;
		this.username = username;
		this.email = email;
		this.status = status;
		this.rolesCsv = rolesCsv;
	}

	/**
	 * <b>【充血模型方便方法】將快照內的 CSV 字串安全還原為 Set</b>
	 * <p>
	 * 方便舊有程式碼或某些特殊鑑權邏輯在不查關係表的情況下，秒還原出角色的強型態集合。
	 * </p>
	 */
	public Set<String> getRolesAsSet() {
		if (this.rolesCsv == null || this.rolesCsv.isBlank()) {
			return Collections.emptySet();
		}
		return Stream.of(this.rolesCsv.split(",")).map(String::trim).collect(Collectors.toSet());
	}

	// ── 手動宣告唯讀原味 Getters（拒絕 Lombok，維護乾淨血統） ──
	public UUID getId() {
		return id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getUsername() {
		return username;
	}

	public String getEmail() {
		return email;
	}

	public String getStatus() {
		return status;
	}

	public String getRolesCsv() {
		return rolesCsv;
	}
}