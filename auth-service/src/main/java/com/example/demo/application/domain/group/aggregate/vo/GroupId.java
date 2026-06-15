package com.example.demo.application.domain.group.aggregate.vo;

import java.util.UUID;

/**
 * <h2>[領域層 - 值物件] 群組強型態識別碼 (Group ID Value Object)</h2>
 */
public record GroupId(UUID value) {
	public GroupId {
		if (value == null)
			throw new IllegalArgumentException("Group ID cannot be null");
	}

	public static GroupId generate() {
		return new GroupId(UUID.randomUUID());
	}

	public static GroupId fromString(String id) {
		return new GroupId(UUID.fromString(id));
	}
}