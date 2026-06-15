package com.example.demo.iface.rest;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.service.UserCommandService;
import com.example.demo.application.service.UserQueryService;
import com.example.demo.application.shared.command.CreateUserCommand;
import com.example.demo.application.shared.dto.UserRepresentation;

/**
 * <h2>[基礎設施層 - Web 適配器] 使用者傳輸控制層 (User Controller)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為系統對外的 RESTful API 入口（Inbound Adapter）。遵循 CQRS（讀寫分離）原則，
 * 變更狀態的請求（POST/PUT/DELETE）分流至 {@link UserCommandService}， 拉取數據的請求（GET）則直接直擊
 * {@link UserQueryService} 以獲取極速快照。
 * </p>
 * <p>
 * <b>【技術細節防線】</b>：<br>
 * 1. <b>點號副檔名截斷防禦</b>：在查詢單筆接口使用 {@code /{username:.+}}，防止 Spring MVC
 * 自動將點號後的字元誤判為副檔名（如 .json）而強行截斷。<br>
 * 2. <b>多租戶隱式隔離</b>：所有接口入參一律不攜帶 tenantId，改由 {@code TenantInterceptor}
 * 自動通電鎖定執行緒，防禦跨租戶越權。
 * </p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserCommandService userCommandService;
	private final UserQueryService userQueryService;

	public UserController(UserCommandService userCommandService, UserQueryService userQueryService) {
		this.userCommandService = userCommandService;
		this.userQueryService = userQueryService;
	}

	// ==========================================
	// ── 寫入側接口 (Command Side - 變更狀態) ──
	// ==========================================

	/**
	 * <b>建立全新使用者 (Create User)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/users}
	 * </p>
	 * 
	 * @param request 包含用戶註冊基本資訊的 JSON 載荷
	 * @return 201 Created 狀態碼，並於 Body 回傳該用戶唯一的業務主鍵 {@code username}
	 */
	@PostMapping
	public ResponseEntity<String> createUser(@RequestBody CreateUserRequest request) {
		CreateUserCommand command = new CreateUserCommand(request.username(), request.password(), request.email());
		// 驅動應用層編排流程
		String username = userCommandService.createUser(command);
		return ResponseEntity.status(HttpStatus.CREATED).body(username);
	}

	/**
	 * <b>修改使用者密碼 (Change Password)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code PUT /api/users/{username}/password}
	 * </p>
	 * 
	 * @param username 業務不可變主鍵
	 */
	@PutMapping("/{username}/password")
	public ResponseEntity<Void> changePassword(@PathVariable String username,
			@RequestBody ChangePasswordRequest request) {

		userCommandService.changePassword(username, request.newPassword());
		return ResponseEntity.noContent().build(); // 204 No Content 代表狀態變更成功且無多餘回傳
	}

	/**
	 * <b>變更使用者基本個人資料 (Update Profile)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code PUT /api/users/{username}/profile}
	 * </p>
	 * <p>
	 * 守護業務規則：遵循新規範，username 落地後終生不可變，本接口僅支持更新 Email。
	 * </p>
	 */
	@PutMapping("/{username}/profile")
	public ResponseEntity<Void> updateUserProfile(@PathVariable String username,
			@RequestBody UpdateUserProfileRequest request) {

		userCommandService.updateUserProfile(username, request.email());
		return ResponseEntity.noContent().build();
	}

	/**
	 * <b>停用/軟刪除使用者 (Deactivate User)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code DELETE /api/users/{username}}
	 * </p>
	 * <p>
	 * 將使用者狀態打上 DEACTIVATED，不執行物理刪除，保留歷史審計血統。
	 * </p>
	 */
	@DeleteMapping("/{username}")
	public ResponseEntity<Void> deleteUser(@PathVariable String username) {
		userCommandService.deactivateUser(username);
		return ResponseEntity.noContent().build();
	}

	/**
	 * <b>🚀 將特定角色賦予指定使用者 (Assign Role to User)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/users/{username}/roles/{roleCode}}
	 * </p>
	 * <p>
	 * <b>設計美感：</b> 路由完全以業務雙主角當家（例：/api/users/alex/roles/ADMIN），網址自帶完美語意，無需額外
	 * Request Body。
	 * </p>
	 */
	@PostMapping("/{username}/roles/{roleCode}")
	public ResponseEntity<Void> assignRoleToUser(@PathVariable String username, @PathVariable String roleCode) {

		// 驅動核心編排：物理綁定關係 ➡️ 觸發 Outbox 落地 ➡️ 非同步刷新 UserView 快照表的角色欄位
		userCommandService.assignRoleToUser(username, roleCode);
		return ResponseEntity.ok().build(); // 200 OK
	}

	// ==========================================
	// ── 讀取側接口 (Query Side - 扁平快照) ──
	// ==========================================

	/**
	 * <b>查詢單一使用者詳細資訊與所持角色 (Read Detail)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/users/{username}}
	 * </p>
	 * <p>
	 * 🚀 <b>硬核防禦：</b> 加上 {@code :.+} 正則匹配，確保諸如 {@code V-NICK.GH.ZHANG}
	 * 等帶有點號的用戶名能被完整捕獲，不發生 404 災難。
	 * </p>
	 */
	@GetMapping("/{username:.+}")
	public ResponseEntity<UserRepresentation> getUser(@PathVariable String username) {
		// 直擊去正規化的 user_view 投影表，效能最優化
		return ResponseEntity.ok(userQueryService.getUserByUsername(username));
	}

	/**
	 * <b>查詢當前租戶空間下的全量使用者清單視圖 (Read List)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/users}
	 * </p>
	 */
	@GetMapping
	public ResponseEntity<List<UserRepresentation>> getAllUsers() {
		return ResponseEntity.ok(userQueryService.getAllUsersOfCurrentTenant());
	}
}

// ── 💡 Web 層專用原生不可變前端 Record DTOs ──

/**
 * 密碼變更請求結構體
 */
record ChangePasswordRequest(String newPassword) {
}

/**
 * 用戶基礎資料變更請求結構體
 */
record UpdateUserProfileRequest(String email) {
}

/**
 * 用戶建立請求結構體
 */
record CreateUserRequest(String username, String password, String email) {
}