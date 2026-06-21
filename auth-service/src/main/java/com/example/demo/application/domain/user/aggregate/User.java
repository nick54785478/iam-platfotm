package com.example.demo.application.domain.user.aggregate;


import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.domain.user.aggregate.vo.AccountInfo;
import com.example.demo.application.domain.user.aggregate.vo.UserId;
import com.example.demo.application.domain.user.aggregate.vo.UserStatus;
import com.example.demo.application.domain.user.event.UserChangedEvent;
import com.example.demo.application.domain.user.event.UserCreatedEvent;
import com.example.demo.application.domain.user.event.UserPasswordChangedEvent;
import com.example.demo.application.domain.shared.event.DomainEvent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <h2>[領域層 - 聚合根] 使用者充血模型 (User Aggregate Root)</h2> *
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為認證子系統的靈魂核心，負責守護使用者賬號狀態、密碼安全、以及與角色關係的業務邊界與不變性（Invariants）。
 * 所有的狀態變更必須透過其內聚的業務方法進行，嚴禁外部直接透過 Setter 進行破壞。
 * </p>
 * *
 * <p>
 * <b>【架構設計美感 - 雙軌制角色設計】</b>：<br>
 * 1. <b>對內物理儲存 (關係型地基)</b>：內部持有 {@code Set<RoleId>} (UUID)。在資料庫物理結構中，透過 ID
 * 進行高效率的外鍵或關聯表儲存。<br>
 * 2. <b>對外事件投影 (強語意主角)</b>：在 CQRS 讀取側視圖（UserView）與外圍 Kafka 廣播中，為了符合以業務鍵為主角的規格，
 * 提供了 {@link #toChangedEvent(Set)} 擴大方法，由應用層在流程編排時，將 UUID 換回人類看得懂的
 * {@code roleCodes} (如 ADMIN) 後再行發射。
 * </p>
 */
public class User {

	/**
	 * 使用者物理主鍵 (UUID 強型態封裝)
	 * */
	private final UserId id;

	/**
	 * 使用者賬號與基礎個人資訊值物件 (不可變)
	 * */
	private AccountInfo accountInfo;

	/**
	 * 賬號當前生命週期狀態
	 * */
	private UserStatus status;

	/**
	 * 連續登入失敗計數器 (防禦暴力破解)
	 * */
	private int failedLoginAttempts;

	/**
	 * 該使用者當前擁有的物理角色識別碼集合
	 * */
	private final Set<RoleId> assignedRoles;

	/**
	 *  核心優化：聚合根內部累積的強型態領域事件列表，待外層 Adapter 儲存成功後拔出發射
	 * */
	private final List<DomainEvent> domainEvents = new ArrayList<>();

	/**
	 * 業務規則：最大連續登入失敗次數，超過即自動鎖定賬號
	 * */
	private static final int MAX_FAILED_ATTEMPTS = 5;

	/**
	 * <b>【重建用建構式】</b><br>
	 * 專用於資料庫還原資料（Rehydration）或工廠內部使用，不包含任何業務規則校驗。
	 */
	public User(UserId id, AccountInfo accountInfo, UserStatus status, int failedLoginAttempts,
			Set<RoleId> assignedRoles) {
		this.id = id;
		this.accountInfo = accountInfo;
		this.status = status;
		this.failedLoginAttempts = failedLoginAttempts;
		this.assignedRoles = new HashSet<>(assignedRoles);
	}

	/**
	 * <b>【業務工廠方法】建立全新的合法使用者</b>
	 *
	 * @param username 唯一賬號名稱 (不可變)
	 * @param encryptedPassword 已經過基礎設施層雜湊加密後的密碼明文
	 * @param email             電子郵件信箱
	 * @return 完全合法的 User 充血實體
	 */
	public static User createNew(String username, String encryptedPassword, String email) {
		User newUser = new User(UserId.generate(), new AccountInfo(username, encryptedPassword, email),
				UserStatus.ACTIVE, 0, new HashSet<>());

		// 1. 註冊建立事件 (專用於審計日誌或特殊觸發)
		newUser.registerEvent(
				new UserCreatedEvent(UUID.randomUUID(), newUser.getId().value(), newUser.getAccountInfo().username(),
						newUser.getAccountInfo().email(), newUser.getStatus().name(), Set.of(), LocalDateTime.now()));

		// 2. 關鍵調整：同時註冊通用變更事件，確保 Projection 讀取側的視圖能即時建立
		newUser.registerEvent(newUser.toChangedEvent());

		return newUser;
	}

	// ── 核心業務邏輯 (Domain Methods) ──

	/**
	 * <b>【變更方法】修改使用者基礎個人資料</b>
	 * <p>
	 * 守護不變性：已被停用的用戶不允許修改資料。依照最新規格，username 設為業務不可變主鍵，因此本方法僅允許修正 Email。
	 * </p>
	 * * @param newEmail 新的電子郵件信箱
	 */
	public void changeProfile(String newEmail) {
		if (this.status == UserStatus.DEACTIVATED) {
			throw new IllegalStateException("Cannot change profile for deactivated user");
		}

		// 狀態變更：保持原本的 username 與密碼不變，替換不可變的值物件
		this.accountInfo = new AccountInfo(this.accountInfo.username(), this.accountInfo.encryptedPassword(), newEmail);

		// 內聚觸發變更事件，通知視圖層同步
		this.registerEvent(this.toChangedEvent());
	}

	/**
	 * <b>【變更方法】安全變更密碼</b>
	 * <p>
	 * 只負責密碼生命週期的業務與發布密碼專用事件，不越權觸發全量 View 投影更新。
	 * </p>
	 * * @param newEncryptedPassword 新的加密密碼
	 */
	public void changePassword(String newEncryptedPassword) {
		if (this.status == UserStatus.DEACTIVATED) {
			throw new IllegalStateException("Cannot change password for deactivated user");
		}
		this.accountInfo = this.accountInfo.changePassword(newEncryptedPassword);

		this.registerEvent(new UserPasswordChangedEvent(UUID.randomUUID(), this.id.value(), LocalDateTime.now()));
	}

	/**
	 * <b>【管理業務】停用使用者（軟刪除商務行為）</b>
	 * <p>
	 * 將賬號狀態變更為 DEACTIVATED，並連帶觸發狀態同步事件，讓讀取側快照同步更新。
	 * </p>
	 */
	public void deactivate() {
		this.status = UserStatus.DEACTIVATED;
		this.registerEvent(this.toChangedEvent());
	}

	/**
	 * <b>【內聚組裝】將當前模型的狀態快照轉為統一的狀態變更事件</b>
	 * <p>
	 * ⚠️ 注意：此處內部組裝僅能提供 {@code Set<String>} 的 UUID 字串。 若要對外提供強語意的 {@code roleCode}（如
	 * ADMIN），請改為呼叫應用層專用的 {@link #toChangedEvent(Set)}。
	 * </p>
	 */
	public UserChangedEvent toChangedEvent() {
		Set<String> roleStrings = this.assignedRoles.stream().map(roleId -> roleId.value().toString())
				.collect(Collectors.toSet());

		return new UserChangedEvent(UUID.randomUUID(), this.id.value(), this.accountInfo.username(),
				this.accountInfo.email(), this.status.name(), roleStrings, LocalDateTime.now());
	}

	/**
	 * <b>【跨聚合還原】組裝帶有真實角色代碼（roleCode）的完全體狀態變更事件</b>
	 * <p>
	 * 為解決「User 僅持有 RoleId，但 View / 外部 Kafka 想看 roleCode 字串」的斷層， 由應用層（Service）查出
	 * Code 列表後灌入本方法，組裝出完美符合 API 規格的完全體事件。
	 * </p>
	 * * @param roleCodes 真實的人類可讀角色代碼集合，例如 ["ADMIN", "SALES"]
	 */
	public UserChangedEvent toChangedEvent(Set<String> roleCodes) {
		return new UserChangedEvent(UUID.randomUUID(), this.id.value(), this.accountInfo.username(),
				this.accountInfo.email(), this.status.name(), roleCodes, LocalDateTime.now());
	}

	/**
	 * <b>【防禦業務】處理登入失敗懲罰行為</b> * @return 若賬號因此次失敗而觸發「鎖定機制」，回傳 true；否則回傳 false
	 */
	public boolean handleFailedLogin() {
		if (this.status != UserStatus.ACTIVE) {
			return false;
		}
		this.failedLoginAttempts++;
		if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
			this.status = UserStatus.LOCKED;
			// 未來可在這裡擴充註冊 UserLockedEvent
			return true;
		}
		return false;
	}

	/**
	 * <b>【業務行為】登入成功，歸零計數器</b>
	 */
	public void handleSuccessfulLogin() {
		if (this.status != UserStatus.ACTIVE) {
			throw new IllegalStateException("User account is not active");
		}
		this.failedLoginAttempts = 0;
	}

	/**
	 * <b>【核心變更】分配角色給使用者 (對內物理綁定)</b>
	 * <p>
	 * 守護不變性：Set 集合天然具備去重特性，自動防禦重複指派同一個角色的防線。
	 * </p>
	 */
	public void assignRole(RoleId roleId) {
		if (roleId == null) {
			throw new IllegalArgumentException("Role ID cannot be null");
		}
		this.assignedRoles.add(roleId);
	}

	/**
	 * <b>【核心變更】撤銷使用者的特定角色</b>
	 */
	public void revokeRole(RoleId roleId) {
		this.assignedRoles.remove(roleId);
	}

	/**
	 * <b>【管理業務】手動解除賬號鎖定</b>
	 */
	public void unlock() {
		if (this.status == UserStatus.LOCKED) {
			this.status = UserStatus.ACTIVE;
			this.failedLoginAttempts = 0;
		}
	}

	// ── 領域事件管理能力方法 ──

	private void registerEvent(DomainEvent event) {
		this.domainEvents.add(event);
	}

	/**
	 * <b>【清空並拔出事件】</b><br>
	 * 供外層持久化適配器（WriterAdapter）在資料庫儲存成功後調用，將肚子裡的事件一次性清空並取出發射。
	 */
	public List<DomainEvent> pullDomainEvents() {
		List<DomainEvent> clearedEvents = new ArrayList<>(this.domainEvents);
		this.domainEvents.clear();
		return clearedEvents;
	}

	// ── Getters (一律回傳 Unmodifiable 唯讀封裝，全面捍衛不變性) ──
	public UserId getId() {
		return id;
	}

	public AccountInfo getAccountInfo() {
		return accountInfo;
	}

	public UserStatus getStatus() {
		return status;
	}

	public int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	public Set<RoleId> getAssignedRoles() {
		return Collections.unmodifiableSet(assignedRoles);
	}
}