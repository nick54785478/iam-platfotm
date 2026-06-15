package com.example.demo.application.shared.dto;

import java.util.Set;
import java.util.UUID;

/**
 * 讀取側專用的不可變 Representation DTO Record
 */
public record GroupRepresentation(UUID id, String groupName, String groupCode, Set<String> memberUserIds,
		Set<String> assignedRoleIds) {
}