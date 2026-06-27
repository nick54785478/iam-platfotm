package com.example.demo.iface.rest;

import com.example.demo.application.service.RoleCommandService;
import com.example.demo.application.service.RoleQueryService;
import com.example.demo.application.shared.dto.RoleRepresentation;
import com.example.demo.iface.dto.req.RoleRequest;
import com.example.demo.iface.dto.res.RoleResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <h2>[基礎設施層 - Web 適配器] 角色與權限控制層 (Role Controller)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別負責角色生命週期（建立、更名）與分散式環境中<b>「各子系統權限點自動上報」</b>的 HTTP 入口對接。 讀取側直接分流至擁有極速 JSON
 * 欄位的 {@link RoleQueryService}，完美實現 CQRS 雙翼並行。
 * </p>
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {

	private final RoleCommandService roleCommandService;
	private final RoleQueryService roleQueryService;

	public RoleController(RoleCommandService roleCommandService, RoleQueryService roleQueryService) {
		this.roleCommandService = roleCommandService;
		this.roleQueryService = roleQueryService;
	}

	// ==========================================
	// ── 寫入側接口 (Command Side - 變更狀態) ──
	// ==========================================

	/**
	 * <b>建立全新自定義角色 (Create Role)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/roles}
	 * </p>
	 */
	@PostMapping
	public ResponseEntity<RoleResponse.RoleCreatedResource> createRole(@RequestBody RoleRequest.CreateRoleRequest resource) {
		roleCommandService.createRole(resource.roleName(), resource.roleCode());
		return new ResponseEntity<>(new RoleResponse.RoleCreatedResource("200", String.format("%s 角色建立成功", resource.roleName()) ), HttpStatus.CREATED); // 201 Created
	}

	/**
	 * <b>角色描述名稱更名 (Rename Role)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code PUT /api/roles/{roleCode}/name}
	 * </p>
	 * <p>
	 * 守護不變性：roleCode 作為核心不變業務鍵，設於路徑中不可修正；本接口僅允許更名 roleName。
	 * </p>
	 */
	@PutMapping("/{roleCode}/name")
	public ResponseEntity<RoleResponse.RoleRenamedResource> renameRole(@PathVariable String roleCode, @RequestBody RoleRequest.RenameRoleRequest resource) {

		roleCommandService.renameRole(roleCode, resource.newName());
		return new ResponseEntity<>(new RoleResponse.RoleRenamedResource("200", String.format("%s 角色建立成功", resource.newName()) ), HttpStatus.CREATED); // 201 Created
	}

	/**
	 * <b>🚀 接收來自各子系統微服務的權限點自動上報 / 賦予</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/roles/{roleCode}/permissions}
	 * </p>
	 * <p>
	 * <b>架構精髓：</b> 供下游微服務（如訂單、商品服務）啟動時，自動將自身開拓的權限點上報給認證中心。 內部具備完全的值物件等價去重與冪等覆蓋更新防線。
	 * </p>
	 */
	@PostMapping("/{roleCode}/permissions")
	public ResponseEntity<RoleResponse.PermissionAssignedResource> assignPermission(@PathVariable String roleCode,
																					@RequestBody RoleRequest.AssignPermissionRequest request) {
		roleCommandService.reportPermission(roleCode, request.systemCode(), request.permissionCode(),
				request.permissionName());
		return new ResponseEntity<>(new RoleResponse.PermissionAssignedResource("200", "Success"), HttpStatus.OK); // 200 OK
	}

	// ==========================================
	// ── 讀取側接口 (Query Side - 扁平視圖) ──
	// ==========================================

	/**
	 * <b>查詢單一角色之詳細資訊與全量權限清單 (Read Detail)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/roles/{roleCode}}
	 * </p>
	 * <p>
	 * 直擊內含 {@code permissions_json} 欄位的 {@code role_view} 投影表，免除傳統一對多的大 JOIN 效能怪獸。
	 * </p>
	 */
	@GetMapping("/{roleCode}")
	public ResponseEntity<RoleResponse.RoleViewGottenResource> getRoleByCode(@PathVariable String roleCode) {
		RoleRepresentation data = roleQueryService.getRoleByCode(roleCode);
		return ResponseEntity.ok(new RoleResponse.RoleViewGottenResource("200", "Success", data));
	}

	/**
	 * <b>查詢當前租戶下的全角色快照清單 (Read List)</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code GET /api/roles}
	 * </p>
	 */
	@GetMapping
	public ResponseEntity<RoleResponse.RolesViewGottenResource> getAllRoles() {
		List<RoleRepresentation> data = roleQueryService.getAllRolesOfCurrentTenant();
		return ResponseEntity.ok(new RoleResponse.RolesViewGottenResource("200", "Success", data));
	}
}






