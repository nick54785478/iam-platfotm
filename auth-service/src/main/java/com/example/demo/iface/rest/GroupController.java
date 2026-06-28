package com.example.demo.iface.rest;

import com.example.demo.application.service.GroupCommandService;
import com.example.demo.application.service.GroupQueryService;
import com.example.demo.application.shared.dto.GroupRepresentation;
import com.example.demo.iface.dto.req.GroupRequest;
import com.example.demo.iface.dto.res.GroupResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
	public ResponseEntity<GroupResponse.GroupCreatedResource> createGroup(@RequestBody GroupRequest.CreateGroupResource request) {
		groupCommandService.createGroup(request.groupName(), request.groupCode());
		return new ResponseEntity<>(new GroupResponse.GroupCreatedResource("200","群組新增成功"), HttpStatus.CREATED); // 201 Created
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
	public ResponseEntity<GroupResponse.GroupRenamedResource> renameGroup(@PathVariable String groupCode, @RequestBody GroupRequest.RenameGroupResource resource) {
		groupCommandService.renameGroup(groupCode, resource.newName());
		return new ResponseEntity<>(new GroupResponse.GroupRenamedResource("200","群組更名成功"), HttpStatus.OK); // 204 No Content
	}

	/**
	 * <b>將指定使用者加入特定群組 (Add Group Member)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/groups/{groupCode}/members/{username}}
	 * </p>
	 * <p>
	 * <b>範例：</b> {@code POST /api/groups/DEPT_TECH/members/alex.zhang}
	 * </p>
	 */
	@PostMapping("/{groupCode}/members/{username}")
	public ResponseEntity<GroupResponse.GroupMemberAddedResource> addMemberToGroup(@PathVariable String groupCode, @PathVariable String username) {

		// 驅動應用層進行跨聚合元數據轉譯綁定
		groupCommandService.addMemberToGroup(groupCode, username);
		return new ResponseEntity<>(new GroupResponse.GroupMemberAddedResource("200", "加入成功"), HttpStatus.OK); // 200 OK
	}

	/**
	 * <b>將指定使用者移出特定群組 (Remove Group Member)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code DELETE /api/groups/{groupCode}/members/{username}}
	 * </p>
	 */
	@DeleteMapping("/{groupCode}/members/{username}")
	public ResponseEntity<GroupResponse.GroupMemberRemovedResource> removeMemberFromGroup(@PathVariable String groupCode, @PathVariable String username) {

		groupCommandService.removeMemberFromGroup(groupCode, username);
		return new ResponseEntity<>(new GroupResponse.GroupMemberRemovedResource("200", "移出成功"), HttpStatus.OK); // 204 No Content
	}

	/**
	 * <b>將特定角色批量賦予群組 (Assign Role to Group)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/groups/{groupCode}/roles/{roleCode}}
	 * </p>
	 * <p>
	 * <b>範例：</b> {@code POST /api/groups/DEPT_TECH/roles/ROLE_DEVELOPER}
	 * </p>
	 */
	@PostMapping("/{groupCode}/roles/{roleCode}")
	public ResponseEntity<GroupResponse.GroupRoleAssignedResource> assignRoleToGroup(@PathVariable String groupCode, @PathVariable String roleCode) {

		groupCommandService.assignRoleToGroup(groupCode, roleCode);
		return new ResponseEntity<>(new GroupResponse.GroupRoleAssignedResource("200",
				String.format("群組角色 %s %s 建立成功", groupCode,roleCode)), HttpStatus.OK); // 200 OK
	}

	/**
	 * <b>撤銷該群組綁定的特定角色 (Revoke Role from Group)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code DELETE /api/groups/{groupCode}/roles/{roleCode}}
	 * </p>
	 */
	@DeleteMapping("/{groupCode}/roles/{roleCode}")
	public ResponseEntity<GroupResponse.GroupRoleRevokedResource> revokeRoleFromGroup(@PathVariable String groupCode, @PathVariable String roleCode) {

		groupCommandService.revokeRoleFromGroup(groupCode, roleCode);
		return new ResponseEntity<>(new GroupResponse.GroupRoleRevokedResource("200",
				String.format("群組角色 %s %s 撤銷成功", groupCode,roleCode)), HttpStatus.OK); // 200 OK
	}

	/**
	 * <b>查詢單一群組詳細成員與角色快照 (Read Detail)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/groups/{groupCode}}
	 * </p>
	 */
	@GetMapping("/{groupCode}")
	public ResponseEntity<GroupResponse.GroupViewGottenResource> getGroupByCode(@PathVariable String groupCode) {
		// 一發複合索引直擊去正規化的 group_view 投影表，效能最優化
		GroupRepresentation groupRepresentation = groupQueryService.getGroupByCode(groupCode);
		return ResponseEntity.ok(new GroupResponse.GroupViewGottenResource("200", "Success", groupRepresentation));
	}

	/**
	 * <b>查詢當前租戶空間下的全量群組視圖清單 (Read List)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/groups}
	 * </p>
	 */
	@GetMapping
	public ResponseEntity<GroupResponse.GroupsViewGottenResource> getAllGroups() {
		List<GroupRepresentation> groupRepresentations = groupQueryService.getAllGroupsOfCurrentTenant();
		return ResponseEntity.ok(new GroupResponse.GroupsViewGottenResource("200", "Success", groupRepresentations));

	}
}

