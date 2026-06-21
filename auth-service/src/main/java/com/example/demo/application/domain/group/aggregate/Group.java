package com.example.demo.application.domain.group.aggregate;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.demo.application.domain.group.aggregate.vo.GroupId;
import com.example.demo.application.domain.group.event.GroupChangedEvent;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.domain.user.aggregate.vo.UserId;
import com.example.demo.application.domain.shared.event.DomainEvent;

/**
 * <h2>[領域層 - 聚合根] 群組充血模型 (Group Aggregate Root)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為批量使用者權限控管的邊界守護者。它負責內聚管理「群組 ➡️ 使用者 (UserId)」以及「群組 ➡️ 角色 (RoleId)」的綁定關係。
 * 負責守護群組名稱合法性、成員去重等業務不變性（Invariants）。
 * </p>
 * <p>
 * <b>【架構設計美感 - 弱引用與高內聚】</b>：<br>
 * 1. <b>弱引用原則 (Id-Only Reference)</b>：群組肚子裡絕不持有 {@code User} 或 {@code Role}
 * 的充血實體， 僅持有 {@code Set<UserId>} 與
 * {@code Set<RoleId>}。這樣能徹底阻斷多聚合根在記憶體中的瘋狂耦合，保持架構清爽。<br>
 * 2. <b>事件驅動自動通電</b>：內部的成員調整與角色指派方法，皆會自動註冊全量 {@link GroupChangedEvent}，與現有的
 * Outbox 監聽器天衣無縫的複用！
 * </p>
 */
public class Group {

	private final GroupId id;
	private String groupName;
	private final String groupCode; // 🚀 規格對齊：作為與前端、子系統對接的主角業務鍵，一經建立便不可變

	/** 該群組內包含的使用者物理 ID 弱引用集合 (Set 天然去重) */
	private final Set<UserId> memberUserIds;

	/** 該群組所被賦予的角色物理 ID 弱引用集合 */
	private final Set<RoleId> assignedRoleIds;

	/** 內部累積的強型態領域事件列表 */
	private final List<DomainEvent> domainEvents = new ArrayList<>();

	/**
	 * <b>【重建用建構式】</b><br>
	 * 專供資料庫還原快照（Rehydration）調用，繞過任何業務防禦校驗。
	 */
	public Group(GroupId id, String groupName, String groupCode, Set<UserId> memberUserIds,
			Set<RoleId> assignedRoleIds) {
		this.id = id;
		this.groupName = groupName;
		this.groupCode = groupCode;
		this.memberUserIds = new HashSet<>(memberUserIds);
		this.assignedRoleIds = new HashSet<>(assignedRoleIds);
	}

	/**
	 * <b>【業務工廠方法】建立全新群組</b>
	 * 
	 * @param groupName 群組名稱，如：「技術部開發一組」
	 * @param groupCode 群組代碼，如：「DEPT_TECH_TEAM_01」
	 */
	public static Group create(String groupName, String groupCode) {
		if (groupCode == null || groupCode.isBlank()) {
			throw new IllegalArgumentException("Group code cannot be empty");
		}
		if (groupName == null || groupName.isBlank()) {
			throw new IllegalArgumentException("Group name cannot be empty");
		}

		Group newGroup = new Group(GroupId.generate(), groupName, groupCode, new HashSet<>(), new HashSet<>());

		// 🚀 建立時自動內聚註冊變更事件，確保 Projection 讀取側視圖同步長出來
		newGroup.registerEvent(newGroup.toChangedEvent());
		return newGroup;
	}

	// ── 核心業務邏輯 (Domain Methods) ──

	/**
	 * <b>【變更方法】修改群組名稱</b>
	 */
	public void rename(String newName) {
		if (newName == null || newName.isBlank()) {
			throw new IllegalArgumentException("Group name cannot be empty");
		}
		this.groupName = newName;
		this.registerEvent(this.toChangedEvent());
	}

	/**
	 * <b>【核心業務】將使用者加入群組（批量賦予權限的地基）</b>
	 * <p>
	 * 守護不变性：Set 容器會自動去重，若用戶早已在群組內，此動作會溫和忽視，防止產生重複數據技術債。
	 * </p>
	 */
	public void addMember(UserId userId) {
		if (userId == null)
			throw new IllegalArgumentException("Member User ID cannot be null");

		boolean added = this.memberUserIds.add(userId);
		if (added) {
			this.registerEvent(this.toChangedEvent()); // 確實有加入成功才發布變更事件
		}
	}

	/**
	 * <b>【核心業務】將使用者移出群組</b>
	 */
	public void removeMember(UserId userId) {
		if (userId == null)
			return;

		boolean removed = this.memberUserIds.remove(userId);
		if (removed) {
			this.registerEvent(this.toChangedEvent());
		}
	}

	/**
	 * <b>【核心業務】指派角色給該群組</b>
	 * <p>
	 * 一經指派，該群組肚子裡的所有使用者成員，將在網關層自動繼承此角色的全量權限點！
	 * </p>
	 */
	public void assignRole(RoleId roleId) {
		if (roleId == null)
			throw new IllegalArgumentException("Role ID cannot be null");

		boolean added = this.assignedRoleIds.add(roleId);
		if (added) {
			this.registerEvent(this.toChangedEvent());
		}
	}

	/**
	 * <b>【核心業務】撤銷該群組的角色</b>
	 */
	public void revokeRole(RoleId roleId) {
		if (roleId == null)
			return;

		boolean removed = this.assignedRoleIds.remove(roleId);
		if (removed) {
			this.registerEvent(this.toChangedEvent());
		}
	}

	/**
	 * <b>【內聚組裝】將當前群組關係，組裝成統一的全量狀態變更事件</b>
	 */
	public GroupChangedEvent toChangedEvent() {
		Set<String> memberIds = this.memberUserIds.stream().map(id -> id.value().toString())
				.collect(Collectors.toSet());

		Set<String> roleIds = this.assignedRoleIds.stream().map(id -> id.value().toString())
				.collect(Collectors.toSet());

		return new GroupChangedEvent(UUID.randomUUID(), // 用於外圈消費端去重的唯一事件 ID
				this.id.value(), this.groupName, this.groupCode, memberIds, roleIds, LocalDateTime.now());
	}

	// ── 領域事件管理能力方法 ──
	private void registerEvent(DomainEvent event) {
		this.domainEvents.add(event);
	}

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> clearedEvents = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return clearedEvents;
	}

	// ── Getters (一律回傳 Unmodifiable 唯讀封裝，全面捍衛不變性) ──
	public GroupId getId() {
		return id;
	}

	public String getGroupName() {
		return groupName;
	}

	public String getGroupCode() {
		return groupCode;
	}

	public Set<UserId> getMemberUserIds() {
		return Collections.unmodifiableSet(memberUserIds);
	}

	public Set<RoleId> getAssignedRoleIds() {
		return Collections.unmodifiableSet(assignedRoleIds);
	}
}