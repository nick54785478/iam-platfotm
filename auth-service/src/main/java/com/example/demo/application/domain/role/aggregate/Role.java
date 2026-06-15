package com.example.demo.application.domain.role.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.demo.application.domain.role.aggregate.vo.Permission;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.domain.role.event.RoleChangedEvent;
import com.example.demo.application.shared.event.DomainEvent;

/**
 * <h2>[領域層 - 聚合根] 角色充血模型 (Role Aggregate Root)</h2> *
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為權限管控體系的邊界守護者，內聚管理角色的基本資訊，以及該角色所持有的跨子系統權限點（Permissions）。
 * 負責阻斷一切不合法的改名、非法撤銷系統內建核心角色的業務行為。
 * </p>
 * *
 * <p>
 * <b>【架構設計美感 - 業務代碼主導制】</b>：<br>
 * 1. <b>不變的業務鍵 (Natural Key)</b>：在現代 SaaS 架構中，{@code roleCode} (例如: ROLE_ADMIN)
 * 是與各子系統對接、鑑權、API 路由的主角，因此在工廠建立後便設為不可變（Immutable）。<br>
 * 2. <b>全量狀態變更通知 (N + Ved + Event)</b>：不論是更名、指派權限或撤銷權限，只要內部狀態有變，便會自動組裝出一發全量的
 * {@link RoleChangedEvent} 塞入累積列表，用以非同步驅動讀取側的投影櫥窗（RoleView）或外圍 Kafka 廣播。
 * </p>
 */
public class Role {

	/**
	 * 角色物理主鍵 (UUID 強型態封裝)
	 */
	private final RoleId id;

	/**
	 * 角色人類可讀名稱，例如：「高級銷售專員」、「系統管理員」
	 */
	private String roleName;

	/**
	 * 規格對齊：作為 API 識別、網關鑑權與子系統識別的主角，一經建立便不可變（Immutable）
	 */
	private final String roleCode;

	/**
	 * 是否為系統內建、防禦不可刪除與不可修改權限的 root 角色
	 */
	private final boolean isSystemRoot;

	/**
	 * 該角色當前擁有的跨子系統權限點值物件集合
	 */
	private final Set<Permission> permissions;

	/**
	 * 核心優化：強型態領域事件累積列表，待外層 Adapter 儲存成功後拔出發射
	 */
	private final List<DomainEvent> domainEvents = new ArrayList<>();

	/**
	 * <b>【重建用建構式】</b><br>
	 * 專用於從資料庫還原快照狀態（Rehydration）或基礎設施層工廠建立，不包含任何業務 Guard Clauses。
	 */
	public Role(RoleId id, String roleName, String roleCode, boolean isSystemRoot, Set<Permission> permissions) {
		this.id = id;
		this.roleName = roleName;
		this.roleCode = roleCode;
		this.isSystemRoot = isSystemRoot;
		this.permissions = new HashSet<>(permissions);
	}

	/**
	 * <b>【業務工廠方法】建立自定義角色</b> * @param roleName 角色名稱 (例如: 財務主管)
	 * 
	 * @param roleCode 角色代碼 (例如: FINANCE_MANAGER)
	 * @return 狀態合法且預設註冊了變更事件的 Role 聚合根
	 */
	public static Role createCustom(String roleName, String roleCode) {
		if (roleCode == null || roleCode.isBlank()) {
			throw new IllegalArgumentException("Role code cannot be empty");
		}

		Role newRole = new Role(RoleId.generate(), roleName, roleCode, false, new HashSet<>());

		// 🚀 建立時自動內聚註冊變更事件，確保 Projection 讀取側的 RoleView 視圖同步被建立
		newRole.registerEvent(newRole.toChangedEvent());
		return newRole;
	}

	// ── 核心業務邏輯 (Domain Methods) ──

	/**
	 * <b>【變更方法】修改角色名稱</b>
	 * <p>
	 * 守護不變性：如果是系統內建的內置角色 (System Root)，硬核阻斷更名，防止系統崩潰。
	 * </p>
	 * * @param newName 新的角色名稱
	 */
	public void rename(String newName) {
		if (this.isSystemRoot) {
			throw new IllegalStateException("Cannot rename a system root role");
		}
		if (newName == null || newName.isBlank()) {
			throw new IllegalArgumentException("Role name cannot be empty");
		}
		this.roleName = newName;

		// 🚀 狀態變更後，觸發全量狀態同步事件
		this.registerEvent(this.toChangedEvent());
	}

	/**
	 * <b>【核心業務】賦予權限點 (支援來自各外部子系統自動上報)</b>
	 * <p>
	 * 冪等更新邏輯：如果該權限點（systemCode + permissionCode）已存在，
	 * 說明是一次「更名上報」，先移除舊的、再塞入新的，達成冪等覆蓋並自動更新名稱描述。
	 * </p>
	 * * @param newPermission 來自子系統的全新權限點值物件
	 */
	public void assignPermission(Permission newPermission) {
		if (newPermission == null) {
			throw new IllegalArgumentException("Permission cannot be null");
		}

		// 基於業務鍵判斷，如果相同就先砍掉，實作自動覆蓋更新
		this.permissions.removeIf(p -> p.isSamePermission(newPermission.systemCode(), newPermission.permissionCode()));
		this.permissions.add(newPermission);

		// 🚀 權限上報或賦予成功，內聚上報事件
		this.registerEvent(this.toChangedEvent());
	}

	/**
	 * <b>【核心業務】撤銷特定子系統的特定權限點</b> * @param systemCode 外部子系統代碼
	 * 
	 * @param permissionCode 權限點唯一代碼
	 */
	public void revokePermission(String systemCode, String permissionCode) {
		if (this.isSystemRoot) {
			throw new IllegalStateException("Cannot alter permissions of a system root role");
		}

		boolean removed = this.permissions.removeIf(p -> p.isSamePermission(systemCode, permissionCode));
		if (removed) {
			// 🚀 真的有撤銷成功才上報事件，防止無效的投影更新
			this.registerEvent(this.toChangedEvent());
		}
	}

	/**
	 * <b>【管理業務】撤銷該角色屬於某個子系統的「所有」權限</b>
	 * <p>
	 * 當某個微服務子系統整個要重構或下線時，會用到這個批次清理業務。
	 * </p>
	 * * @param systemCode 欲整批撤銷的子系統代碼 (例如: order-service)
	 */
	public void revokeAllPermissionsOfSystem(String systemCode) {
		if (this.isSystemRoot) {
			throw new IllegalStateException("Cannot alter permissions of a system root role");
		}
		if (systemCode == null || systemCode.isBlank()) {
			return;
		}

		boolean removed = this.permissions.removeIf(p -> p.systemCode().equals(systemCode));
		if (removed) {
			this.registerEvent(this.toChangedEvent());
		}
	}

	/**
	 * <b>【內聚組裝】將當前角色的最新快照狀態，組裝成統一的 RoleChangedEvent</b>
	 * <p>
	 * 把正規化的 {@code Permission} 摺疊展開成扁平化的 DTO Record，供讀取側一發 Upsert JSON 使用。
	 * </p>
	 */
	public RoleChangedEvent toChangedEvent() {
		Set<RoleChangedEvent.PermissionInfo> permissionInfos = this.permissions.stream()
				.map(p -> new RoleChangedEvent.PermissionInfo(p.systemCode(), p.permissionCode(), p.permissionName()))
				.collect(Collectors.toSet());

		return new RoleChangedEvent(UUID.randomUUID(), // 唯一的 eventId，用於發射到外圈時的消費端分布式去重
				this.id.value(), this.roleName, this.roleCode, this.isSystemRoot, permissionInfos, LocalDateTime.now());
	}

	// ── 領域事件管理能力方法 ──

	private void registerEvent(DomainEvent event) {
		this.domainEvents.add(event);
	}

	/**
	 * <b>【清空並拔出事件】</b><br>
	 * 供外層持久化適配器（RoleWriterAdapter）在 save 成功後調用，無感打包進多租戶信封發射。
	 */
	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> clearedEvents = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return clearedEvents;
	}

	// ── Getters (一律回傳 Unmodifiable 唯讀封裝，捍衛領域核心邊界) ──
	public RoleId getId() {
		return id;
	}

	public String getRoleName() {
		return roleName;
	}

	public String getRoleCode() {
		return roleCode;
	}

	public boolean isSystemRoot() {
		return isSystemRoot;
	}

	public Set<Permission> getPermissions() {
		return Collections.unmodifiableSet(permissions);
	}
}