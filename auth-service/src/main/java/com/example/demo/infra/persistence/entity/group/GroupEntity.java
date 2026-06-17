package com.example.demo.infra.persistence.entity.group;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.demo.application.domain.group.aggregate.Group;
import com.example.demo.application.domain.group.aggregate.vo.GroupId;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.domain.user.aggregate.vo.UserId;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * <h2>[基礎設施層 - 儲存模型] 群組持久化實體 (Group Database Entity)</h2>
 * <p>
 * <b>【設計天職】</b>：<br>
 * 本類別為群組寫入側（Command Side）的技術持久化實體。它映射了主表 {@code auth_groups}。 內部透過兩組
 * {@code @ElementCollection} 自動展開並對映兩張輕量級的一對多關係表 （{@code auth_group_members} 與
 * {@code auth_group_roles}），在關係型資料庫中將弱引用 Id 集合安全落地。
 * </p>
 */
@Entity
@Table(name = "auth_groups", indexes = {
		// 🚀 頂規優化：加上專屬表名前綴，與讀取側 View 複合索引名稱乾淨錯開
		@Index(name = "idx_auth_groups_tenant_code", columnList = "tenant_id, group_code", unique = true) })
public class GroupEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	@Column(name = "group_name", nullable = false)
	private String groupName;

	@Column(name = "group_code", nullable = false, updatable = false)
	private String groupCode; // 🚀 作為 API 識別的主角業務鍵，落地後物理不可變

	/** 🚀 物理落地：群組內包含的使用者 UUID 關係表 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "auth_group_members", joinColumns = @JoinColumn(name = "group_id"))
	@Column(name = "user_id")
	private Set<UUID> memberUserIds = new HashSet<>();

	/** 🚀 物理落地：群組被賦予的角色 UUID 關係表 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "auth_group_roles", joinColumns = @JoinColumn(name = "group_id"))
	@Column(name = "role_id")
	private Set<UUID> assignedRoleIds = new HashSet<>();

	/** JPA 規範要求的無參建構式 */
	protected GroupEntity() {
	}

	/**
	 * <b>【充血轉換】Domain 模型 ➡️ DB Entity 儲存結構</b>
	 */
	public static GroupEntity fromDomain(Group group, String tenantId) {
		GroupEntity entity = new GroupEntity();
		entity.id = group.getId().value();
		entity.tenantId = tenantId;
		entity.groupCode = group.getGroupCode();
		entity.updateFromDomain(group); // 複用更新邏輯
		return entity;
	}

	/**
	 * <b>【狀態同步】將 Domain 的變更安全同步進本 DB 實體</b>
	 */
	public void updateFromDomain(Group group) {
		this.groupName = group.getGroupName();

		// ⚠️ 物理對映關鍵點：將 Domain 的強型態 UserId/RoleId 拆開成純 UUID 灌入 JPA 儲存集合
		this.memberUserIds = group.getMemberUserIds().stream().map(UserId::value).collect(Collectors.toSet());

		this.assignedRoleIds = group.getAssignedRoleIds().stream().map(RoleId::value).collect(Collectors.toSet());
	}

	/**
	 * <b>【充血還原 - Rehydration】DB 實體 ➡️ 還原為充血領域模型</b>
	 */
	public Group toDomain() {
		Set<UserId> domainUserIds = this.memberUserIds.stream().map(UserId::new).collect(Collectors.toSet());

		Set<RoleId> domainRoleIds = this.assignedRoleIds.stream().map(RoleId::new).collect(Collectors.toSet());

		return new Group(new GroupId(this.id), this.groupName, this.groupCode, domainUserIds, domainRoleIds);
	}

	// ── 手動宣告唯讀原味 Getters（拒絕 Lombok，維護乾淨血統） ──
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

	public Set<UUID> getMemberUserIds() {
		return memberUserIds;
	}

	public Set<UUID> getAssignedRoleIds() {
		return assignedRoleIds;
	}
}