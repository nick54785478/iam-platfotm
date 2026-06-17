package com.example.demo.infra.persistence.entity.user;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.domain.user.aggregate.vo.AccountInfo;
import com.example.demo.application.domain.user.aggregate.vo.UserId;
import com.example.demo.application.domain.user.aggregate.vo.UserStatus;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * <h2>[基礎設施層 - 儲存模型] 使用者持久化實體 (User Database Entity)</h2>
 * <p>
 * <b>【設計天職】</b>：<br>
 * 本實體映射使用者主表 {@code auth_users}。特別之處在於它內部宣告了 {@code @ElementCollection}
 * 來持有該用戶關聯的角色 UUID 集合（表名：{@code user_roles}）。 這種設計守住了「User 聚合根只通過物理 ID 引用
 * Role」的 DDD 高級原則，在資料庫物理結構中完成落地。
 * </p>
 */
@Entity
@Table(name = "auth_users", uniqueConstraints = {
		@UniqueConstraint(name = "uk_auth_users_tenant_username", columnNames = { "tenant_id", "username" }) })
public class UserEntity {

	@Id
	@Column(name = "id", updatable = false, nullable = false)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	@Column(name = "username", nullable = false)
	private String username;

	@Column(name = "password", nullable = false)
	private String password;

	@Column(name = "email", nullable = false)
	private String email;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "failed_attempts", nullable = false)
	private int failedLoginAttempts;

	/** 🚀 對內物理綁定：使用者持有的角色 ID (UUID) 集合表 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "role_id")
	private Set<UUID> assignedRoleIds = new HashSet<>();

	protected UserEntity() {
	}

	/**
	 * <b>【充血轉換】Domain 模型 ➡️ DB Entity 儲存結構</b>
	 */
	public static UserEntity fromDomain(User user, String tenantId) {
		UserEntity entity = new UserEntity();
		entity.id = user.getId().value();
		entity.tenantId = tenantId;
		entity.updateFromDomain(user);
		return entity;
	}

	/**
	 * <b>【狀態同步】將 Domain 狀態變更完整覆蓋回本儲存體</b>
	 */
	public void updateFromDomain(User user) {
		this.username = user.getAccountInfo().username();
		this.password = user.getAccountInfo().encryptedPassword();
		this.email = user.getAccountInfo().email();
		this.status = user.getStatus().name();
		this.failedLoginAttempts = user.getFailedLoginAttempts();

		// ⚠️ 物理對映關鍵點：將 Domain 強型態 RoleId 拆開，把內部的 UUID 灌入 JPA 儲存集合
		this.assignedRoleIds = user.getAssignedRoles().stream().map(RoleId::value).collect(Collectors.toSet());
	}

	/**
	 * <b>【充血還原 - Rehydration】DB Entity ➡️ 還原為可執行業務邏輯的充血 Domain 模型</b>
	 */
	public User toDomain() {
		Set<RoleId> roles = this.assignedRoleIds.stream().map(RoleId::new).collect(Collectors.toSet());

		return new User(new UserId(this.id), new AccountInfo(this.username, this.password, this.email),
				UserStatus.valueOf(this.status), this.failedLoginAttempts, roles);
	}

	// ── 堅決拒絕大面積 Setter，維護原汁原味的原生封裝 Getter ──
	public UUID getId() {
		return id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getEmail() {
		return email;
	}

	public String getStatus() {
		return status;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Set<UUID> getAssignedRoleIds() {
		return assignedRoleIds;
	}
}