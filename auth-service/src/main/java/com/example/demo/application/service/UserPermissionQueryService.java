package com.example.demo.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.port.GroupReaderPort;
import com.example.demo.application.port.RoleReaderPort;
import com.example.demo.application.port.UserReaderPort;
import com.example.demo.application.shared.dto.GroupRepresentation;
import com.example.demo.application.shared.dto.UserPermissionContextRepresentation;
import com.example.demo.application.shared.dto.UserPermissionContextRepresentation.GroupRoleInfo;
import com.example.demo.application.shared.dto.UserPermissionContextRepresentation.PermissionDto;
import com.example.demo.application.shared.dto.UserRepresentation;
import com.example.demo.infra.context.TenantContext;

@Service
@Transactional(readOnly = true)
public class UserPermissionQueryService {

	private final UserReaderPort userReaderPort;
	private final GroupReaderPort groupReaderPort;
	private final RoleReaderPort roleReaderPort;

	public UserPermissionQueryService(UserReaderPort userReaderPort, GroupReaderPort groupReaderPort,
			RoleReaderPort roleReaderPort) {
		this.userReaderPort = userReaderPort;
		this.groupReaderPort = groupReaderPort;
		this.roleReaderPort = roleReaderPort;
	}

	public UserPermissionContextRepresentation getUserPermissionContext(String username) {
		String currentTenantId = TenantContext.getCurrentTenantId();

		// 1. 透過 Port 獲取用戶 Representation (內含個人持有的角色代碼)
		UserRepresentation userDto = userReaderPort.fetchByUsername(currentTenantId, username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));

		String userIdStr = userDto.id().toString();
		Set<String> personalRoles = userDto.roles();

		// 2. 透過 Port 獲取該租戶全量群組 DTO，並在記憶體內高效過濾
		List<GroupRepresentation> allGroups = groupReaderPort.fetchAllByTenant(currentTenantId);

		List<GroupRepresentation> userBelongedGroups = allGroups.stream()
				.filter(g -> g.memberUserIds().contains(userIdStr)).toList();

		// 3. 收集與聚合所有涉及的角色代碼契約
		Set<String> allTargetRoleCodes = new HashSet<>(personalRoles);
		Set<GroupRoleInfo> groupRoleInfos = new HashSet<>();

		for (GroupRepresentation g : userBelongedGroups) {
			Set<String> groupRoleCodes = new HashSet<>();

			// 🚀 完美對流：拿著 UUID 透過 Port 換回人類可讀 Role
			g.assignedRoleIds().forEach(roleIdUuidStr -> {
				roleReaderPort.fetchByUuid(currentTenantId, UUID.fromString(roleIdUuidStr))
						.ifPresent(roleDto -> groupRoleCodes.add(roleDto.roleCode()));
			});

			allTargetRoleCodes.addAll(groupRoleCodes);
			groupRoleInfos.add(new GroupRoleInfo(g.groupCode(), g.groupName(), groupRoleCodes));
		}

		// 4. 🚀 記憶體聯集去重：撈取這些角色的權限，由 Set 特性自動 Union 完成！
		Set<PermissionDto> finalPermissions = new HashSet<>();
		for (String roleCode : allTargetRoleCodes) {
			roleReaderPort.fetchByRoleCode(currentTenantId, roleCode).ifPresent(roleDto -> {
				roleDto.permissions().forEach(p -> finalPermissions
						.add(new PermissionDto(p.systemCode(), p.permissionCode(), p.permissionName())));
			});
		}

		return new UserPermissionContextRepresentation(userDto.username(), userDto.email(), userDto.status(),
				personalRoles, groupRoleInfos, finalPermissions);
	}
}