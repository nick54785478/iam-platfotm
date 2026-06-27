package com.example.demo.iface.rest;

import com.example.demo.application.service.PermissionDictQueryService;
import com.example.demo.application.shared.dto.PermissionDictGottenResult;
import com.example.demo.iface.dto.res.PermissionDictResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <h2>[介面層/被動適配器] 權限字典查詢 REST API (Query Side Controller)</h2>
 * <p>
 * 專責處理權限清單的讀取請求，支援模組過濾與關鍵字搜尋。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/permissions/dict")
@RequiredArgsConstructor
public class PermissionDictController {

    // 💡 遵循 CQRS，Controller 直接依賴 QueryPort，完全繞過 CommandService
    private final PermissionDictQueryService queryService;

    /**
     * <b>【查詢 API】獲取權限字典清單</b>
     *
     * @param tenantId 從 API 網關透傳的租戶 Header (強制隔離)
     * @param module   (Optional) 目標模組，例如 "Department"
     * @param keyword  (Optional) 模糊搜尋關鍵字
     * @return 200 OK 權限清單陣列
     */
    @GetMapping
    public ResponseEntity<PermissionDictResponse.PermissionDictGottenResource> getPermissions(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(value = "module", required = false) String module,
            @RequestParam(value = "keyword", required = false) String keyword) {

        List<PermissionDictGottenResult> data = queryService.searchPermissions(tenantId, module, keyword);
        return ResponseEntity.ok(new PermissionDictResponse.PermissionDictGottenResource("200", "Success", data));
    }
}