package com.example.demo.infra.persistence.entity.user.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * <h2>[基礎設施層 - 值組件] 權限持久化對應組件 (Embeddable VO)</h2>
 */
@Embeddable
public class PermissionEmbeddable {

	@Column(name = "system_code", nullable = false)
	private String systemCode;

	@Column(name = "permission_code", nullable = false)
	private String permissionCode;

	@Column(name = "permission_name", nullable = false)
	private String permissionName;

	protected PermissionEmbeddable() {
	}

	public PermissionEmbeddable(String sc, String pc, String pn) {
		this.systemCode = sc;
		this.permissionCode = pc;
		this.permissionName = pn;
	}

	public String getSystemCode() {
		return systemCode;
	}

	public String getPermissionCode() {
		return permissionCode;
	}

	public String getPermissionName() {
		return permissionName;
	}
}