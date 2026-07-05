package com.example.demo.infra.persistence.entity;

import com.example.demo.application.domain.user.aggregate.UserProfile;
import com.example.demo.application.domain.user.aggregate.vo.LanguagePreference;
import com.example.demo.application.domain.user.aggregate.vo.ProfileInfo;
import com.example.demo.application.domain.user.aggregate.vo.ThemePreference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <h2>[基礎設施層] 個人檔案資料庫實體</h2>
 */
@Entity
@Table(name = "user_profiles")
@Getter
@NoArgsConstructor
public class UserProfileEntity {

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private String id; // 與 User 共享的物理主鍵

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private String tenantId;

	// 將 ProfileInfo VO 打平存儲
	@Column(name = "display_name")
	private String displayName;

	@Column(name = "avatar_url")
	private String avatarUrl;

	@Column(name = "bio", length = 500)
	private String bio;

	@Enumerated(EnumType.STRING)
	@Column(name = "language", nullable = false)
	private LanguagePreference language;

	@Enumerated(EnumType.STRING)
	@Column(name = "theme", nullable = false)
	private ThemePreference theme;

	@Version
	@Column(name = "version")
	private Long version;

	// ── 防腐層映射邏輯 (Mapping) ──

	public static UserProfileEntity fromDomain(UserProfile domain, String tenantId) {
		UserProfileEntity entity = new UserProfileEntity();
		entity.id = domain.getId();
		entity.tenantId = tenantId;
		entity.displayName = domain.getProfileInfo().displayName();
		entity.avatarUrl = domain.getProfileInfo().avatarUrl();
		entity.bio = domain.getProfileInfo().bio();
		entity.language = domain.getLanguage();
		entity.theme = domain.getTheme();
		entity.version = domain.getVersion();
		return entity;
	}

	public void updateFromDomain(UserProfile domain) {
		this.displayName = domain.getProfileInfo().displayName();
		this.avatarUrl = domain.getProfileInfo().avatarUrl();
		this.bio = domain.getProfileInfo().bio();
		this.language = domain.getLanguage();
		this.theme = domain.getTheme();
		this.version = domain.getVersion();
	}

	public UserProfile toDomain() {
		// 利用聚合根的「重建建構式 (Rehydration Constructor)」還原
		return new UserProfile(this.id, new ProfileInfo(this.displayName, this.avatarUrl, this.bio), this.language,
				this.theme, this.version);
	}
}