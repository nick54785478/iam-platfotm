package com.example.demo.iface.rest;

import com.example.demo.application.service.ApiResourceRuleCommandService;
import com.example.demo.application.shared.command.inbound.CreateApiRuleCommand;
import com.example.demo.application.shared.command.inbound.UpdateApiRuleCommand;
import com.example.demo.iface.dto.req.CreateApiRuleResource;
import com.example.demo.iface.dto.req.UpdateApiRuleResource;
import com.example.demo.iface.dto.res.ApiRuleCreatedResource;
import com.example.demo.iface.dto.res.ApiRuleStatusToggledResource;
import com.example.demo.iface.dto.res.ApiRuleUpdatedResource;
import com.example.demo.infra.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>[介面層] API 資源授權規則管理台</h2>
 * <p>
 * 專供系統超級管理員動態維護各 API 節點的權限門檻。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/departments/api-rules")
@RequiredArgsConstructor
@RequiresPermission("platform:RULE_MANAGE") // 整座 Controller 都在最高權限防護傘下
public class ApiResourceRuleAdminController {

    private final ApiResourceRuleCommandService commandService;

    @PostMapping
    public ResponseEntity<ApiRuleCreatedResource> createRule(
            @RequestHeader("X-Tenant-Id") String tenantId,   // 由 Gateway 注入的租戶 ID
            @RequestHeader("X-User-Id") String operator,     // 由 Gateway 注入的操作者 ID (供稽核)
            @RequestBody CreateApiRuleResource request) {

        log.debug("[API] 收到建立 API 規則請求，租戶: {}, 操作者: {}, 路徑: {}", tenantId, operator, request.pathPattern());

        // 邊界轉譯 (Anti-Corruption)：將外部 Resource 結合 HTTP Headers 組裝成內部 Command
        CreateApiRuleCommand command = new CreateApiRuleCommand(
                tenantId,
                request.httpMethod(),
                request.pathPattern(),
                request.requiredPermission(),
                request.priority()
        );

        // 將組裝好的 Command 遞交給應用層
        Long newRuleId = commandService.createRule(command);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiRuleCreatedResource("201", "Success", newRuleId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiRuleUpdatedResource> updateRule(
            @RequestHeader("X-Tenant-Id") String tenantId,   // 強制擷取租戶
            @RequestHeader("X-User-Id") String operator,     // 強制擷取操作者
            @PathVariable("id") Long id,
            @RequestBody UpdateApiRuleResource request) {

        // 邊界轉譯與組裝
        UpdateApiRuleCommand cmd = new UpdateApiRuleCommand(
                tenantId, id, request.httpMethod(), request.pathPattern(),
                request.requiredPermission(), request.priority(), operator
        );

        commandService.updateRule(cmd);
        return ResponseEntity.ok(new ApiRuleUpdatedResource("200", "Success"));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiRuleStatusToggledResource> toggleStatus(
            @RequestHeader("X-Tenant-Id") String tenantId,   // 強制擷取租戶
            @RequestHeader("X-User-Id") String operator,
            @PathVariable("id") Long id,
            @RequestParam("active") boolean isActive) {

        commandService.toggleRuleStatus(tenantId, id, isActive, operator);
        return ResponseEntity.ok(new ApiRuleStatusToggledResource("200", "Success", isActive));
    }
}