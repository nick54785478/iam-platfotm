package com.example.demo.application.domain.user.aggregate;

import java.util.ArrayList;
import java.util.List;

import com.example.demo.application.domain.shared.event.DomainEvent;
import com.example.demo.application.domain.user.aggregate.vo.LanguagePreference;
import com.example.demo.application.domain.user.aggregate.vo.ProfileInfo;
import com.example.demo.application.domain.user.aggregate.vo.ThemePreference;
import com.example.demo.application.domain.user.event.UserProfileUpdatedEvent;

/**
 * <h2>[領域層 - 聚合根] 使用者個人配置模型 (User Profile Aggregate)</h2>
 */
public class UserProfile {

	/**
	 * 共享身分標識 (1:1 關係，等同於 Aggregate ID，已調整為純字串)
	 */
	private final String id;

	private ProfileInfo profileInfo;
	private LanguagePreference language;
	private ThemePreference theme;
	private Long version;

	private final List<DomainEvent> domainEvents = new ArrayList<>();

	/**
	 * <b>【重建用建構式】</b>
	 */
	public UserProfile(String id, ProfileInfo profileInfo, LanguagePreference language, ThemePreference theme,
			Long version) {
		this.id = id;
		this.profileInfo = profileInfo;
		this.language = language;
		this.theme = theme;
		this.version = version;
	}

	/**
	 * <b>【業務工廠方法】建立預設的個人檔案</b>
	 */
	public static UserProfile createDefault(String userId, String defaultDisplayName) {
		return new UserProfile(userId, ProfileInfo.defaultOf(defaultDisplayName), LanguagePreference.EN_US,
				ThemePreference.SYSTEM_DEFAULT, 0L);
	}

	// ── 核心業務邏輯 (Domain Methods) ──

	public void updateProfileInfo(String displayName, String avatarUrl, String bio, String tenantId, String operator) {
		this.profileInfo = new ProfileInfo(displayName, avatarUrl, bio);
		this.version++;
		this.registerUpdateEvent(tenantId, operator);
	}

	public void changeLanguage(LanguagePreference newLanguage, String tenantId, String operator) {
		if (newLanguage == null) {
			throw new IllegalArgumentException("Language preference cannot be null");
		}
		this.language = newLanguage;
		this.version++;
		this.registerUpdateEvent(tenantId, operator);
	}

	public void changeTheme(ThemePreference newTheme, String tenantId, String operator) {
		if (newTheme == null) {
			throw new IllegalArgumentException("Theme preference cannot be null");
		}
		this.theme = newTheme;
		this.version++;
		this.registerUpdateEvent(tenantId, operator);
	}

	/**
	 * <b>【內部防腐】統一打包並發射更新事件</b>
	 */
	private void registerUpdateEvent(String tenantId, String operator) {
		this.domainEvents.add(new UserProfileUpdatedEvent(tenantId, this.id, // 🚀 直接傳遞 String 類型的 ID
				this.profileInfo.displayName(), this.profileInfo.avatarUrl(), this.profileInfo.bio(),
				this.language.name(), this.theme.name(), operator, this.version));
	}

	// ── 領域事件管理能力方法 ──

	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> clearedEvents = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return clearedEvents;
	}

	// ── Getters ──

	public String getId() {
		return id; // 回傳型別同步調整為 String
	}

	public ProfileInfo getProfileInfo() {
		return profileInfo;
	}

	public LanguagePreference getLanguage() {
		return language;
	}

	public ThemePreference getTheme() {
		return theme;
	}

	public Long getVersion() {
		return version;
	}
}