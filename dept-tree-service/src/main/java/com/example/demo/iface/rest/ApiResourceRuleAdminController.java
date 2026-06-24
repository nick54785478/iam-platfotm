package com.example.demo.iface.rest;

import com.example.demo.application.service.ApiResourceRuleCommandService;
import com.example.demo.application.shared.command.inbound.CreateApiRuleCommand;
import com.example.demo.application.shared.command.inbound.UpdateApiRuleCommand;
import com.example.demo.iface.dto.res.ApiRuleCreatedResource;
import com.example.demo.iface.dto.res.ApiRuleStatusToggledResource;
import com.example.demo.iface.dto.res.ApiRuleUpdatedResource;
import com.example.demo.infra.annotation.RequiresPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>[介面層] API 資源授權規則管理台</h2>
 * <p>
 * 專供系統超級管理員動態維護各 API 節點的權限門檻。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/platform/api-rules")
@RequiredArgsConstructor
@RequiresPermission("platform:RULE_MANAGE") // 🛡️ 整座 Controller 都在最高權限防護傘下
public class ApiResourceRuleAdminController {

    private final ApiResourceRuleCommandService commandService;

    @PostMapping
    public ResponseEntity<ApiRuleCreatedResource> createRule(@RequestBody CreateApiRuleCommand request) {
        Long newRuleId = commandService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiRuleCreatedResource("201", "Success", newRuleId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiRuleUpdatedResource> updateRule(
            @PathVariable("id") Long id,
            @RequestBody UpdateApiRuleCommand request) {

        // 確保路徑 ID 與 Payload ID 一致
        UpdateApiRuleCommand cmd = new UpdateApiRuleCommand(
                id, request.httpMethod(), request.pathPattern(), request.requiredPermission(), request.priority()
        );
        commandService.updateRule(cmd);
        return new ResponseEntity<>(new  ApiRuleUpdatedResource("200", "Success"), HttpStatus.OK);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiRuleStatusToggledResource> toggleStatus(
            @PathVariable("id") Long id,
            @RequestParam("active") boolean isActive) {

        commandService.toggleRuleStatus(id, isActive);
        return new ResponseEntity<>(new  ApiRuleStatusToggledResource("200", "Success", isActive), HttpStatus.OK);
    }
}