package com.example.demo.iface.rest;

import com.example.demo.application.service.PermissionCommandService;
import com.example.demo.application.shared.command.inbound.DefinePermissionCommand;
import com.example.demo.application.shared.command.inbound.UpdatePermissionCommand;
import com.example.demo.iface.dto.req.DefinePermissionResource;
import com.example.demo.iface.dto.req.UpdatePermissionResource;
import com.example.demo.iface.dto.res.PermissionDefinedResource;
import com.example.demo.iface.dto.res.PermissionUpdatedResource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * <h2>[介面層/主動適配器] 權限控制中心命令端 REST API (Command Side Adapter)</h2>
 * <p>
 * 專責處理權限定義、宣告與變更的寫入端控制器。
 * 遵循 CQRS 原則，此處的所有操作皆不返回厚重的領域實體，僅返回唯一識別碼或標準狀態。
 * </p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/departments/permissions")
@RequiredArgsConstructor
public class PermissionCommandController {

    private final PermissionCommandService permissionCommandService;

    /**
     * <b>【API 1】自動宣告或等冪覆蓋更新權限字典</b>
     * <p>
     * 適用於基礎設施啟動時的自動掃描註冊，或平台管理員在前端手動配置。
     * 底層實作具備等冪性 (Upsert 語意)。
     * </p>
     *
     * @param tenantId 多租戶識別碼 (嚴格由企業網關封裝並透傳)
     * @param operator 操作者 ID / 系統識別碼 (供 Audit Trail 稽核軌跡使用)
     * @param resource 傳入的權限宣告 Payload
     * @return 201 Created 並於 Body 攜帶生成的權限 ID
     */
    @PostMapping
    public ResponseEntity<PermissionDefinedResource> definePermission(
            @RequestHeader("X-Tenant-Id") @NotBlank(message = "租戶識別碼不可為空") String tenantId,
            @RequestHeader("X-User-Id") @NotBlank(message = "操作者識別碼不可為空") String operator,
            @Valid @RequestBody DefinePermissionResource resource) {

        log.info("[Permission-API] 接收到權限宣告請求. Tenant: [{}], Code: [{}], Operator: [{}]",
                tenantId, resource.code(), operator);

        // 轉譯為應用層專屬的 Command DTO
        DefinePermissionCommand command = new DefinePermissionCommand(resource.code(), resource.name(), resource.description(),
                resource.module());

        // 驅動核心服務
        String permissionId = permissionCommandService.definePermission(tenantId, command, operator);

        // 遵循 RESTful 規範，全新宣告或等冪異動皆返回輕量化 ID 對照
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new PermissionDefinedResource("200", "Success", permissionId));
    }

    /**
     * <b>【API 2】手動更新權限細節描述</b>
     * <p>
     * 專供 REST API 手動調整既有權限的非核心業務屬性（名稱、描述、隸屬模組）。
     * </p>
     *
     * @param tenantId 多租戶識別碼
     * @param operator 操作者 ID
     * @param code     URL 路由參數中的權限代碼 (例如: "USER_CREATE")
     * @param resource 變更細節 Payload
     * @return 200 OK 成功變更回應
     */
    @PutMapping("/{code}")
    public ResponseEntity<PermissionUpdatedResource> updatePermissionDetails(
            @RequestHeader("X-Tenant-Id") @NotBlank(message = "租戶識別碼不可為空") String tenantId,
            @RequestHeader("X-User-Id") @NotBlank(message = "操作者識別碼不可為空") String operator,
            @PathVariable("code") String code,
            @Valid @RequestBody UpdatePermissionResource resource) {

        log.info("[Permission-API] 接收到權限細節變更請求. Tenant: [{}], Code: [{}], Operator: [{}]",
                tenantId, code, operator);

        // 轉譯為應用層變更指令
        UpdatePermissionCommand command = new UpdatePermissionCommand(resource.name(),
                resource.description(), resource.module());

        // 驅動核心服務執行變更
        permissionCommandService.updatePermissionDetails(tenantId, code, command, operator);

        return ResponseEntity.ok(
                new PermissionUpdatedResource("200", "權限更新成功"));
    }
}