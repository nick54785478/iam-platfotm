package com.example.demo.application.port;

import java.util.Optional;
import java.util.Set;

import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;

public interface RoleWriterPort {

	Optional<Role> findById(RoleId id);

	Optional<Role> findByRoleCode(String roleCode); // 🚀 規格對齊

	void save(Role role);

	void delete(Role role); // 用於物理或邏輯刪除角色

	/**
	 * 🚀 補齊寫入側規格：將一整群物理 RoleId 批次還原，並提取出人類可讀的角色代碼 (roleCode)
	 */
	Set<String> findRoleCodesByRoleIds(Set<RoleId> roleIds);

	/**
	 * 🚀 補齊寫入側規格：傳入一整群人類可讀的角色代碼，去關係表聯集出最終扁平化的「系統權限點」字串集合
	 * 回傳格式例：["order-service:ORDER_VIEW", "auth-service:USER_CREATE"]
	 */
	Set<String> findPermissionStringsByRoleCodes(Set<String> roleCodes);
}