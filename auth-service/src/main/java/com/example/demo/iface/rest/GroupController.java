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

import com.example.demo.application.service.GroupCommandService;
import com.example.demo.application.service.GroupQueryService;
import com.example.demo.application.shared.dto.GroupRepresentation;

/**
 * <h2>[基礎設施層 - Web 適配器] 使用者群組傳輸控制層 (Group Controller)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為群組權限管理模組對外的 RESTful API 入口（Inbound Adapter）。 負責將前端發起的 HTTP
 * 請求解碼，並將業務主角代碼（groupCode, username, roleCode）原汁原味地派發給
 * {@link GroupCommandService}。
 * </p>
 * <p>
 * <b>【技術聯防】</b>：<br>
 * 1. <b>極致 RESTful 路由語意</b>：成員與角色的關係綁定全面採用巢狀路徑規格，無需額外的 JSON Body
 * 負載，天然防止越權與數據篡改。<br>
 * 2. <b>多租戶隱式邊界</b>：全面依靠攔截器通電，自動將流量隔離在當前安全租戶空間內。
 * </p>
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

	private final GroupCommandService groupCommandService;
	private final GroupQueryService groupQueryService;

	public GroupController(GroupCommandService groupCommandService, GroupQueryService groupQueryService) {
		this.groupCommandService = groupCommandService;
		this.groupQueryService = groupQueryService;
	}

	// ==========================================
	// ── 寫入側接口 (Command Side - 變更狀態) ──
	// ==========================================

	/**
	 * <b>建立全新使用者群組 (Create Group)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/groups}
	 * </p>
	 */
	@PostMapping
	public ResponseEntity<Void> createGroup(@RequestBody CreateGroupRequest request) {
		groupCommandService.createGroup(request.groupName(), request.groupCode());
		return ResponseEntity.status(HttpStatus.CREATED).build(); // 201 Created
	}

	/**
	 * <b>群組名稱更名描述 (Rename Group)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code PUT /api/groups/{groupCode}/name}
	 * </p>
	 * 
	 * @param groupCode 業務不可變唯一代碼
	 */
	@PutMapping("/{groupCode}/name")
	public ResponseEntity<Void> renameGroup(@PathVariable String groupCode, @RequestBody RenameGroupRequest request) {

		groupCommandService.renameGroup(groupCode, request.newName());
		return ResponseEntity.noContent().build(); // 204 No Content
	}

	/**
	 * <b>🚀 將指定使用者加入特定群組 (Add Group Member)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/groups/{groupCode}/members/{username}}
	 * </p>
	 * <p>
	 * <b>範例：</b> {@code POST /api/groups/DEPT_TECH/members/alex.zhang}
	 * </p>
	 */
	@PostMapping("/{groupCode}/members/{username}")
	public ResponseEntity<Void> addMemberToGroup(@PathVariable String groupCode, @PathVariable String username) {

		// 驅動應用層進行跨聚合元數據轉譯綁定
		groupCommandService.addMemberToGroup(groupCode, username);
		return ResponseEntity.ok().build(); // 200 OK
	}

	/**
	 * <b>🚀 將指定使用者移出特定群組 (Remove Group Member)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code DELETE /api/groups/{groupCode}/members/{username}}
	 * </p>
	 */
	@DeleteMapping("/{groupCode}/members/{username}")
	public ResponseEntity<Void> removeMemberFromGroup(@PathVariable String groupCode, @PathVariable String username) {

		groupCommandService.removeMemberFromGroup(groupCode, username);
		return ResponseEntity.noContent().build(); // 204 No Content
	}

	/**
	 * <b>🚀 將特定角色批量賦予群組 (Assign Role to Group)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/groups/{groupCode}/roles/{roleCode}}
	 * </p>
	 * <p>
	 * <b>範例：</b> {@code POST /api/groups/DEPT_TECH/roles/ROLE_DEVELOPER}
	 * </p>
	 */
	@PostMapping("/{groupCode}/roles/{roleCode}")
	public ResponseEntity<Void> assignRoleToGroup(@PathVariable String groupCode, @PathVariable String roleCode) {

		groupCommandService.assignRoleToGroup(groupCode, roleCode);
		return ResponseEntity.ok().build(); // 200 OK
	}

	/**
	 * <b>🚀 撤銷該群組綁定的特定角色 (Revoke Role from Group)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code DELETE /api/groups/{groupCode}/roles/{roleCode}}
	 * </p>
	 */
	@DeleteMapping("/{groupCode}/roles/{roleCode}")
	public ResponseEntity<Void> revokeRoleFromGroup(@PathVariable String groupCode, @PathVariable String roleCode) {

		groupCommandService.revokeRoleFromGroup(groupCode, roleCode);
		return ResponseEntity.noContent().build(); // 204 No Content
	}

	/**
	 * <b>查詢單一群組詳細成員與角色快照 (Read Detail)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/groups/{groupCode}}
	 * </p>
	 */
	@GetMapping("/{groupCode}")
	public ResponseEntity<GroupRepresentation> getGroupByCode(@PathVariable String groupCode) {
		// 一發複合索引直擊去正規化的 group_view 投影表，效能最優化
		return ResponseEntity.ok(groupQueryService.getGroupByCode(groupCode));
	}

	/**
	 * <b>查詢當前租戶空間下的全量群組視圖清單 (Read List)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/groups}
	 * </p>
	 */
	@GetMapping
	public ResponseEntity<List<GroupRepresentation>> getAllGroups() {
		return ResponseEntity.ok(groupQueryService.getAllGroupsOfCurrentTenant());
	}
}

// ── 💡 Web 層專用不可變前端 Record DTOs ──

/** 群組建立請求結構體 */
record CreateGroupRequest(String groupName, String groupCode) {
}

/** 群組更名請求結構體 */
record RenameGroupRequest(String newName) {
}