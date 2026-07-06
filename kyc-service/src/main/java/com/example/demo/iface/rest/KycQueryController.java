package com.example.demo.iface.rest;

import com.example.demo.application.service.KycQueryService;
import com.example.demo.application.shared.dto.KycGottenResult;
import com.example.demo.application.shared.dto.KycPersonalDetailResult;
import com.example.demo.application.shared.dto.PageQueriedResult;
import com.example.demo.iface.dto.res.AuditQueueSearchedResource;
import com.example.demo.iface.dto.res.KycPersonalDetailGottenResource;
import com.example.demo.infra.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <h2>[介面層 - 輸入適配器] KYC 讀取端 REST API 控制器</h2>
 * <p>
 * 專職處理 HTTP 協議層面的職責（GET 請求、分頁參數接收、404 狀態碼轉譯）。
 * 透過解耦的 {@link KycQueryService} 獲取資料，絕不將資料庫實體（Entity）洩漏給前端。
 * </p>
 */
@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycQueryController {

    private final KycQueryService kycQueryService;

    /**
     * <b>【API】使用者查詢自身的完整 KYC 審核明細</b>
     * <p>回傳包含明碼的完整表單資料，供前端渲染 "我的設定" 頁面</p>
     */
    @GetMapping("/me")
    public ResponseEntity<KycPersonalDetailGottenResource> getMyKycStatus(
            @RequestHeader("X-User-Id") String userId) {

        String currentTenantId = TenantContext.getCurrentTenantId();

        // 呼叫新的明碼查詢方法
        KycPersonalDetailResult result = kycQueryService.getMyKycDetail(currentTenantId, userId);
        return ResponseEntity.ok(new KycPersonalDetailGottenResource("200", "Success", result));
    }

    /**
     * <b>【API】後台管理員分頁條件篩選審核佇列</b>
     * <p>對應網關配置的 /api/kyc/** 路由，並受限流與熔斷器保護。</p>
     *
     * @param status 欲篩選的審核狀態 (例如: PENDING_REVIEW)
     * @param page   頁碼，預設為第 0 頁
     * @param size   每頁筆數，預設為 20 筆
     */
    @GetMapping("/violations")
    public ResponseEntity<AuditQueueSearchedResource> getAuditQueue(
            @RequestParam(value = "status", defaultValue = "PENDING_REVIEW") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        // 1. 貫徹多租戶物理隔離，後台主管只能查到自己所屬租戶的員工資料
        String currentTenantId = TenantContext.getCurrentTenantId();

        // 2. 發動分頁查詢
        PageQueriedResult<KycGottenResult> result =
                kycQueryService.listAuditQueue(currentTenantId, status, page, size);

        return ResponseEntity.ok(new AuditQueueSearchedResource("200", "Success",
                result));
    }
}