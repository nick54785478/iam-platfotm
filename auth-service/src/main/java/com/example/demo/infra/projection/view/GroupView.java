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
 * <h2>[基礎設施層 - 投影視圖] 群組關係極速讀取實體 (Group View)</h2>
 * <p>
 * <b>【設計天職】</b>：<br>
 * 本實體專為 CQRS 讀取側（Query Side）服務。其精髓在於利用<b>去正規化（Denormalized）摺疊技術</b>，
 * 將群組原本極度複雜的使用者成員關係、角色對應關係，分別壓縮並摺疊進兩個單一欄位的 CSV 字串中 （{@code member_user_ids_csv}
 * 與 {@code assigned_role_ids_csv}）。<br>
 * 徹底終結後台在分頁查詢群組清單、或網關層在聯集過濾權限時，因多對多關係所引發的連環 {@code JOIN} 與 {@code N+1} 查詢地獄。
 * </p>
 */
@Entity
@Table(name = "group_view", indexes = {
		// 🚀 頂規優化：建立租戶內的 group_code 複合唯一索引，確保外圈以 groupCode 查詢時速度飛快
		@Index(name = "idx_group_view_tenant_code", columnList = "tenant_id, group_code", unique = true),
		@Index(name = "idx_group_view_tenant_id", columnList = "tenant_id, id") })
public class GroupView {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id; // 🟢 物理主鍵依然保持 UUID，守住與寫入側聚合根的一致性錨點

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	@Column(name = "group_name", nullable = false)
	private String groupName;

	@Column(name = "group_code", nullable = false, updatable = false)
	private String groupCode; // 🚀 API 查詢與網關鑑權的主角業務鍵

	/** 🚀 CQRS 摺疊技術：將群組內的所有成員 UserId (UUID) 拉平成字串，如 "uuid1,uuid2,uuid3" */
	@Column(name = "member_user_ids_csv", columnDefinition = "TEXT")
	private String memberUserIdsCsv;

	/** 🚀 CQRS 摺疊技術：將群組綁定的所有 RoleId (UUID) 拉平成字串，如 "uuidA,uuidB" */
	@Column(name = "assigned_role_ids_csv", columnDefinition = "TEXT")
	private String assignedRoleIdsCsv;

	/** JPA 規範要求的無參建構式 */
	protected GroupView() {
	}

	/** 全參建構式：供背景 Projection 投影處理器全新建立或覆蓋快照時調用 */
	public GroupView(UUID id, String tenantId, String groupName, String groupCode, String memberUserIdsCsv,
			String assignedRoleIdsCsv) {
		this.id = id;
		this.tenantId = tenantId;
		this.groupName = groupName;
		this.groupCode = groupCode;
		this.memberUserIdsCsv = memberUserIdsCsv;
		this.assignedRoleIdsCsv = assignedRoleIdsCsv;
	}

	/**
	 * <b>將快照內的成員 CSV 字串還原為 Set</b>
	 */
	public Set<String> getMemberUserIdsAsSet() {
		if (this.memberUserIdsCsv == null || this.memberUserIdsCsv.isBlank()) {
			return Collections.emptySet();
		}
		return Stream.of(this.memberUserIdsCsv.split(",")).map(String::trim).collect(Collectors.toSet());
	}

	/**
	 * <b>將快照內的角色 CSV 字串還原為 Set</b>
	 */
	public Set<String> getAssignedRoleIdsAsSet() {
		if (this.assignedRoleIdsCsv == null || this.assignedRoleIdsCsv.isBlank()) {
			return Collections.emptySet();
		}
		return Stream.of(this.assignedRoleIdsCsv.split(",")).map(String::trim).collect(Collectors.toSet());
	}

	// ── 手動宣告唯讀原味 Getters（維護乾淨血統） ──
	public UUID getId() {
		return id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getGroupName() {
		return groupName;
	}

	public String getGroupCode() {
		return groupCode;
	}

	public String getMemberUserIdsCsv() {
		return memberUserIdsCsv;
	}

	public String getAssignedRoleIdsCsv() {
		return assignedRoleIdsCsv;
	}
}