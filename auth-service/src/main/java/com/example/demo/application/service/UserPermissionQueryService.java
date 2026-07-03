package com.example.demo.application.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.demo.application.shared.dto.RoleRepresentation;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
		log.debug("[Permission-Query] 開始組裝使用者權限上下文: {}", username);

		// 1. 獲取用戶 Representation (內含個人角色，可能混雜 UUID 與 Code)
		UserRepresentation userDto = userReaderPort.fetchByUsername(currentTenantId, username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));
		String userIdStr = userDto.id();

		// 2. 獲取所屬群組 (內含群組角色，可能混雜 UUID 與 Code)
		List<GroupRepresentation> userBelongedGroups = groupReaderPort.fetchAllByTenant(currentTenantId)
				.stream()
				.filter(g -> g.memberUserIds().contains(userIdStr))
				.toList();

		// =====================================================================
		// 步驟 3：[防禦性收集] 掃描所有 Role 字串，將 UUID 挑出準備批次查詢
		// =====================================================================
		Set<UUID> uuidsToFetch = new HashSet<>();

		for (String roleStr : userDto.roles()) {
			try { uuidsToFetch.add(UUID.fromString(roleStr)); } catch (IllegalArgumentException ignored) {}
		}
		for (GroupRepresentation g : userBelongedGroups) {
			for (String roleStr : g.assignedRoleIds()) {
				try { uuidsToFetch.add(UUID.fromString(roleStr)); } catch (IllegalArgumentException ignored) {}
			}
		}

		// =====================================================================
		// 步驟 4：[批次轉譯] 透過 Port 一口氣撈回 UUID 對應的 Role Code
		// =====================================================================
		Map<String, String> uuidToCodeMap = new HashMap<>();
		if (!uuidsToFetch.isEmpty()) {
			roleReaderPort.fetchByUuids(currentTenantId, uuidsToFetch).forEach(role ->
					uuidToCodeMap.put(role.id(), role.roleCode())
			);
		}

		// =====================================================================
		// 步驟 5：[幾何組裝] 將原始混雜的字串，全部淨化為純淨的 Role Code
		// =====================================================================
		Set<String> cleanPersonalRoleCodes = new HashSet<>();
		for (String roleStr : userDto.roles()) {
			if (uuidToCodeMap.containsKey(roleStr)) {
				cleanPersonalRoleCodes.add(uuidToCodeMap.get(roleStr));
			} else {
				// 如果是 UUID 但 DB 找不到 (幽靈關聯)，拋棄；否則視為明文 Code (如 ADMIN) 保留
				try { UUID.fromString(roleStr); } catch (IllegalArgumentException e) { cleanPersonalRoleCodes.add(roleStr); }
			}
		}

		Set<GroupRoleInfo> groupRoleInfos = new HashSet<>();
		for (GroupRepresentation g : userBelongedGroups) {
			Set<String> cleanGroupRoleCodes = new HashSet<>();
			for (String roleStr : g.assignedRoleIds()) {
				if (uuidToCodeMap.containsKey(roleStr)) {
					cleanGroupRoleCodes.add(uuidToCodeMap.get(roleStr));
				} else {
					try { UUID.fromString(roleStr); } catch (IllegalArgumentException e) { cleanGroupRoleCodes.add(roleStr); }
				}
			}
			groupRoleInfos.add(new GroupRoleInfo(g.groupCode(), g.groupName(), cleanGroupRoleCodes));
		}

		// =====================================================================
		// 步驟 6：[聯集查權限] 聚合所有的 Role Code，利用 flatMap 進行最後的極速查詢
		// =====================================================================
		Set<String> allTargetRoleCodes = new HashSet<>(cleanPersonalRoleCodes);
		groupRoleInfos.forEach(gri -> allTargetRoleCodes.addAll(gri.roleCodes()));

		Set<PermissionDto> finalPermissions = new HashSet<>();
		if (!allTargetRoleCodes.isEmpty()) {
			List<RoleRepresentation> rolesWithPermissions = roleReaderPort.fetchByRoleCodes(currentTenantId, allTargetRoleCodes);

			finalPermissions = rolesWithPermissions.stream()
					.flatMap(role -> role.permissions().stream())
					.map(p -> new PermissionDto(p.systemCode(), p.permissionCode(), p.permissionName()))
					.collect(Collectors.toSet());
		}

		// 7. 封裝並回傳
		return new UserPermissionContextRepresentation(
				userDto.username(),
				userDto.email(),
				userDto.status(),
				cleanPersonalRoleCodes,
				groupRoleInfos,
				finalPermissions
		);
	}
}