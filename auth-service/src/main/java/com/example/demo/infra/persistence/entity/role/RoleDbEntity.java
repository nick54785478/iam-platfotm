package com.example.demo.infra.persistence.entity.role;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.role.aggregate.vo.Permission;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.infra.persistence.entity.user.vo.PermissionEmbeddable;

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
 * <h2>[基礎設施層 - 儲存模型] 角色持久化實體 (Role Database Entity)</h2>
 * <p>
 * <b>【設計天職】</b>：<br>
 * 本類別為寫入側（Command Side）的技術映射。它完全負責與資料庫關係型結構對齊。 其內部的
 * {@code Set<PermissionEmbeddable>} 採用標準 JPA 的 {@code @ElementCollection}，
 * 自動映射出一張獨立的權限關聯表 {@code auth_roles_permissions}。
 * </p>
 * <p>
 * <b>【技術聯防】</b>：<br>
 * 1. <b>保留字安全防禦</b>：表名重新核定為 {@code auth_roles}，安全避開 H2 / Oracle 對於 "ROLES"
 * 的保留字封鎖地雷。<br>
 * 2. <b>充血轉換閉環</b>：內聚了領域對流方法（{@code fromDomain},
 * {@code toDomain}），確保基礎設施層與領域層無痛解耦。
 * </p>
 */
@Entity
@Table(name = "auth_roles", indexes = {
		// 加上明確的表名前綴 auth_roles，與讀取側 View 複合索引名稱乾淨錯開
		@Index(name = "idx_auth_roles_tenant_code", columnList = "tenant_id, role_code", unique = true) })
public class RoleDbEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	@Column(name = "role_name", nullable = false)
	private String roleName;

	@Column(name = "role_code", nullable = false, updatable = false)
	private String roleCode;

	@Column(name = "is_system_root", nullable = false)
	private boolean isSystemRoot;

	/** 將 Permission 值物件對應成一對多的內嵌值元件表 (auth_roles_permissions) */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "auth_roles_permissions", joinColumns = @JoinColumn(name = "role_id"))
	private Set<PermissionEmbeddable> permissions = new HashSet<>();

	protected RoleDbEntity() {
	}

	/**
	 * <b>【充血轉換】Domain 模型 ➡️ DB Entity 儲存結構</b>
	 */
	public static RoleDbEntity fromDomain(Role role, String tenantId) {
		RoleDbEntity entity = new RoleDbEntity();
		entity.id = role.getId().value();
		entity.tenantId = tenantId;
		entity.roleCode = role.getRoleCode();
		entity.updateFromDomain(role); // 複用更新邏輯
		return entity;
	}

	/**
	 * <b>【狀態同步】將 Domain 的變更安全同步進本 DB 實體</b>
	 */
	public void updateFromDomain(Role role) {
		this.roleName = role.getRoleName();
		this.isSystemRoot = role.isSystemRoot();
		this.permissions = role.getPermissions().stream()
				.map(p -> new PermissionEmbeddable(p.systemCode(), p.permissionCode(), p.permissionName()))
				.collect(Collectors.toSet());
	}
	
	/**
	 * <b>【充血還原 - Rehydration】DB 實體 ➡️ 還原為充血領域模型</b>
	 */
	public Role toDomain() {
		Set<Permission> domainPermissions = this.permissions.stream()
				.map(p -> new Permission(p.getSystemCode(), p.getPermissionCode(), p.getPermissionName()))
				.collect(Collectors.toSet());
		
		return new Role(new RoleId(this.id), this.roleName, this.roleCode, this.isSystemRoot, domainPermissions);
	}

	// Getters 保持封裝
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

	public Set<PermissionEmbeddable> getPermissions() {
		return permissions;
	}
}