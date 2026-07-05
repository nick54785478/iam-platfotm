package com.example.demo.application.domain.user.aggregate.vo;

import java.util.UUID;

/**
 * <h2>[領域層 - 值物件] 使用者強型態識別碼 (User ID Value Object)</h2>
 * <p>
 * 杜絕在領域層代碼中直接傳遞毫無業務語意的原始 {@link UUID} 或 {@code String}。 透過強型態包裹，防止在編寫 Service
 * 時不小心將 RoleId 與 UserId 錯位傳入的低級 Bug。
 * </p>
 */
public record UserId(UUID value) {

	public UserId {
		if (value == null) {
			throw new IllegalArgumentException("User ID cannot be null");
		}
	}

	/**
	 * 業務工廠：生成一個全宇宙唯一的全新使用者物理主鍵
	 */
	public static UserId generate() {
		return new UserId(UUID.randomUUID());
	}

	/**
	 * 從標準 UUID 字串還原強型態識別碼
	 */
	public static UserId fromString(String id) {
		return new UserId(UUID.fromString(id));
	}
}