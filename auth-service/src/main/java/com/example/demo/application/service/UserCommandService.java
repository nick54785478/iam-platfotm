package com.example.demo.application.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.port.PasswordEncoderPort;
import com.example.demo.application.port.RoleWriterPort;
import com.example.demo.application.port.UserWriterPort;
import com.example.demo.application.shared.command.CreateUserCommand;
import com.example.demo.application.shared.envelope.TenantEventEnvelope;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[應用層 - 服務] 使用者命令編排服務 (User Command Service)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為寫入側（Command Side）大腦，負責編排所有與使用者生命週期相關的業務流程（新增、修改密碼、更名、軟刪除、指派角色）。
 * 它不包含核心業務邏輯，而是負責防禦檢查、調用領域適配器（Ports）撈出充血模型、驅動領域模型做出變更，最後閉環存檔。
 * </p>
 * <p>
 * <b>【技術亮點】</b>：<br>
 * 1. <b>ACID 事務防線</b>：全類別標註 {@code @Transactional}，確保業務與 Outbox
 * 落地共享同一個資料庫連接與事務。<br>
 * 2. <b>跨聚合根元數據還原</b>：在 {@link #assignRoleToUser(String, String)} 中，解決了 User
 * 內部僅持久化 Role UUID， 但外圈視圖與 Kafka 期待人類可讀代碼的斷層，在應用層流暢拼接並組裝事件拋出。
 * </p>
 */
@Service
@Transactional // 🚀 啟動寫入事務，保障業務與 Outbox 事件落地的原子性
public class UserCommandService {

	private final UserWriterPort userWriterPort;
	private final PasswordEncoderPort passwordEncoderPort;
	private final RoleWriterPort roleWriterPort;
	private final ApplicationEventPublisher eventPublisher;

	public UserCommandService(UserWriterPort userWriterPort, PasswordEncoderPort passwordEncoderPort,
			RoleWriterPort roleWriterPort, ApplicationEventPublisher eventPublisher) {
		this.userWriterPort = userWriterPort;
		this.passwordEncoderPort = passwordEncoderPort;
		this.roleWriterPort = roleWriterPort;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * <b>建立全新使用者</b>
	 * <p>
	 * 守護業務規則：在當前租戶空間內，使用者的 username 必須絕對唯一。
	 * </p>
	 * 
	 * @return 新建用戶的業務主鍵 username，供外層 Controller 進行 RESTful 響應
	 */
	public String createUser(CreateUserCommand command) {
		// 1. 業務規則檢查：利用 Port 從當前租戶空間防禦用戶名重複
		if (userWriterPort.findByUsername(command.username()).isPresent()) {
			throw new IllegalArgumentException("Username '" + command.username() + "' already exists");
		}

		// 2. 呼叫加密適配器
		String encryptedPassword = passwordEncoderPort.encode(command.password());

		// 3. 呼叫充血模型工廠，內部自動註冊 UserCreatedEvent 與初始 UserChangedEvent
		User newUser = User.createNew(command.username(), encryptedPassword, command.email());

		// 4. 存檔（Adapter 內部會自動拔出事件，包進 TenantEventEnvelope 拋出）
		userWriterPort.save(newUser);

		return newUser.getAccountInfo().username();
	}

	/**
	 * <b>變更密碼</b>
	 * <p>
	 * 完全以業務代碼 username 為主角進行編排，不暴露物理 UUID。
	 * </p>
	 */
	public void changePassword(String username, String newPassword) {
		// 1. 依據租戶與 username 撈出充血模型
		User user = userWriterPort.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));

		// 2. 基礎設施加密後，交由領域模型處理密碼變更不變性（內部註冊密碼變更事件）
		String encryptedPassword = passwordEncoderPort.encode(newPassword);
		user.changePassword(encryptedPassword);

		// 3. 存檔更新
		userWriterPort.save(user);
	}

	/**
	 * <b>變更用戶基本資料</b>
	 * <p>
	 * 守護不可變性：依據新規範，僅允許修改 Email，username 落地後即終生不可變。
	 * </p>
	 */
	public void updateUserProfile(String username, String newEmail) {
		User user = userWriterPort.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));

		// 呼叫聚合根方法（內部自動註冊全量 UserChangedEvent，但內部預設只帶 UUID）
		user.changeProfile(newEmail);

		// 🚀 補充還原真實角色代碼，確保發出的事件與視圖包含正確的字串而非 UUID
		Set<String> actualRoleCodes = roleWriterPort.findRoleCodesByRoleIds(user.getAssignedRoles());
		user.confirmRoleAssignmentsForView(actualRoleCodes);

		userWriterPort.save(user);
	}

	/**
	 * <b>軟刪除 / 停用使用者</b>
	 */
	public void deactivateUser(String username) {
		User user = userWriterPort.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));

		// 執行軟刪除領域業務行為
		user.deactivate();

		// 🚀 補充還原真實角色代碼，確保發出的事件與視圖包含正確的字串而非 UUID
		Set<String> actualRoleCodes = roleWriterPort.findRoleCodesByRoleIds(user.getAssignedRoles());
		user.confirmRoleAssignmentsForView(actualRoleCodes);

		userWriterPort.save(user);
	}

	/**
	 * <b>🚀 將角色指派給指定使用者 (完全體分散式去重版)</b>
	 * <p>
	 * <b>【核心設計】</b>：<br>
	 * 為了避免 User 聚合根直接強引用 Role 聚合根（破壞 DDD 邊界），User 肚子裡只存 {@code RoleId} (UUID)。
	 * 但為了讓讀取側投影表 {@code user_view} 能秒讀角色字串陣列（如 ["ADMIN"]），應用層在存檔後， 親自利用 Port 把 UUID
	 * 轉化回真實的 {@code roleCode}，組裝成最完美的事件信封發射。
	 * </p>
	 */
	public void assignRoleToUser(String username, String roleCode) {
		// 1. 依據業務雙主角代碼，撈出兩邊獨立的聚合根
		User user = userWriterPort.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));
		Role role = roleWriterPort.findByRoleCode(roleCode)
				.orElseThrow(() -> new IllegalArgumentException("Role '" + roleCode + "' not found"));

		// 2. 領域行為：物理綁定 UUID 關係
		user.assignRole(role.getId());

		// 3. 持久化（JPA 會將關係寫入 user_roles 表）
		userWriterPort.save(user);

		// 🚀 4. 解決斷層：利用 roleWriterPort，把 User 當前持有的所有 RoleId 統統轉譯成真實的 roleCode 字串
		Set<String> actualRoleCodes = new HashSet<>();
		user.getAssignedRoles().forEach(roleId -> {
			roleWriterPort.findById(roleId).ifPresent(r -> actualRoleCodes.add(r.getRoleCode()));
		});

		// 🚀 5. 親自組裝全量狀態完全體事件，包裹多租戶信封拋出！觸發讀取側視圖 CSV 刷新
		String currentTenantId = TenantContext.getCurrentTenantId();
		eventPublisher.publishEvent(new TenantEventEnvelope(currentTenantId, user.toChangedEvent(actualRoleCodes)));
	}


}