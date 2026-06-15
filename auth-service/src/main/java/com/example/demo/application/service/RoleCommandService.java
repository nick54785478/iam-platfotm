package com.example.demo.application.service;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.role.aggregate.vo.Permission;
import com.example.demo.application.port.RoleWriterPort;


/**
 * <h2>[應用層 - 服務] 角色與權限命令編排服務 (Role Command Service)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別管控所有權限、角色的狀態變更流程。特別是作為認證中心， 它承接了來自各微服務子系統（透過 HTTP 或是 Kafka
 * 傳入）的<b>「自動權限點上報」</b>核心調度功能。
 * </p>
 */
@Service
@Transactional // 🚀 確保全量更新與 Outbox 寫入具備原子性
public class RoleCommandService {

	private final RoleWriterPort roleWriterPort;

	public RoleCommandService(RoleWriterPort roleWriterPort) {
		this.roleWriterPort = roleWriterPort;
	}

	/**
	 * <b>建立自定義角色</b>
	 * <p>
	 * 守護業務規則：同一個租戶空間下，不可建立重複的 roleCode。
	 * </p>
	 */
	public void createRole(String roleName, String roleCode) {
		if (roleWriterPort.findByRoleCode(roleCode).isPresent()) {
			throw new IllegalArgumentException("Role code '" + roleCode + "' already exists");
		}

		// 呼叫工廠方法（內部自動註冊 RoleChangedEvent 確保 Projection 讀取模型即時生成）
		Role role = Role.createCustom(roleName, roleCode);

		roleWriterPort.save(role);
	}

	/**
	 * <b>角色更名</b>
	 * <p>
	 * 完全以業務代碼 roleCode 為主角，不暴露 UUID。
	 * </p>
	 */
	public void renameRole(String roleCode, String newName) {
		Role role = roleWriterPort.findByRoleCode(roleCode)
				.orElseThrow(() -> new IllegalArgumentException("Role code '" + roleCode + "' not found"));

		role.rename(newName);
		roleWriterPort.save(role);
	}

	/**
	 * <b>核心精髓：處理來自各個子系統「自動上報/指派」的權限點</b>
	 * <p>
	 * 不論是管理員在介面手動點選，還是下游微服務啟動時自動透過 Kafka 監聽上報，一律走此通道。 聚合根內部會基於 Value Object
	 * 自動辨識是全新權限還是描述更名，達成完全冪等上報。
	 * </p>
	 */
	public void reportPermission(String roleCode, String systemCode, String permissionCode, String permissionName) {
		Role role = roleWriterPort.findByRoleCode(roleCode)
				.orElseThrow(() -> new IllegalArgumentException("Role code '" + roleCode + "' not found"));

		// 1. 組裝領域層的不可變值物件
		Permission permission = new Permission(systemCode, permissionCode, permissionName);

		// 2. 命令聚合根去消化、去重、或更新這個權限（內部註冊變更事件）
		role.assignPermission(permission);

		// 3. 存檔閉環
		roleWriterPort.save(role);
	}

	/**
	 * <b>撤銷角色的特定子系統權限點</b>
	 */
	public void revokePermission(String roleCode, String systemCode, String permissionCode) {
		Role role = roleWriterPort.findByRoleCode(roleCode)
				.orElseThrow(() -> new IllegalArgumentException("Role code '" + roleCode + "' not found"));

		role.revokePermission(systemCode, permissionCode);
		roleWriterPort.save(role);
	}
}