package com.example.demo.application.service;

import com.example.demo.application.port.KycQueryRepositoryPort;
import com.example.demo.application.shared.dto.KycGottenResult;
import com.example.demo.application.shared.dto.KycPersonalDetailResult;
import com.example.demo.application.shared.dto.PageQueriedResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <h2>[應用層 - 查詢服務] KYC 讀取端應用程式服務</h2>
 * <p>
 * 專職負責讀取側的業務編排。本服務屬於內圈核心，不包含任何資料庫技術（如 JPA/Hibernate），
 * 僅依賴 {@link KycQueryRepositoryPort} 介面進行唯讀事實的調度。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycQueryService {

    private final KycQueryRepositoryPort queryRepositoryPort;

    /**
     * <b>【查詢編排】獲取特定用戶的 KYC 快照事實</b>
     * <p>若資料庫尚無記錄，則回傳 UNVERIFIED 的預設空物件，提升前端開發體驗。</p>
     */
    public KycGottenResult getMyKycStatus(String tenantId, String userId) {
        log.debug("[Kyc-QueryService] 準備獲取用戶 KYC 狀態快照. Tenant: {}, User: {}", tenantId, userId);

        return queryRepositoryPort.getKycStatus(tenantId, userId)
                .orElseGet(() -> {
                    log.debug("[Kyc-QueryService] 查無資料，回傳 UNVERIFIED 預設物件. User: {}", userId);
                    // 核心優化：實作 Null Object Pattern
                    return new KycGottenResult(
                            userId,
                            null, // fullName
                            null, // maskedNationalId
                            "UNVERIFIED", // 💡 賦予預設的業務狀態
                            null, // rejectReason
                            LocalDateTime.now()
                    );
                });
    }

    /**
     * <b>【查詢編排】獲取本人的完整 KYC 明細 (包含明碼)</b>
     */
    public KycPersonalDetailResult getMyKycDetail(String tenantId, String userId) {
        log.debug("[Kyc-QueryService] 準備獲取用戶 KYC 完整明細. Tenant: {}, User: {}", tenantId, userId);

        return queryRepositoryPort.getPersonalDetail(tenantId, userId)
                .orElseGet(() -> {
                    log.debug("[Kyc-QueryService] 查無明細，回傳 UNVERIFIED 預設明細物件. User: {}", userId);

                    // 核心修復：對齊最新的 KycPersonalDetailResult 欄位定義 (共 11 個參數)
                    return new KycPersonalDetailResult(
                            userId,               // 1. userId
                            null,                 // 2. firstName
                            null,                 // 3. lastName
                            null,                 // 4. nationalIdNumber
                            null,                 // 5. nationalIdCountry
                            null,                 // 6. documentType
                            null,                 // 7. dateOfBirth
                            null,                 // 8. fullAddress
                            "UNVERIFIED",         // 9. status            (賦予預設業務狀態)
                            null,                 // 10. rejectReason
                            LocalDateTime.now()   // 11. lastUpdatedAt
                    );
                });
    }

    /**
     * <b>【查詢編排】後台審核列表分頁查詢</b>
     */
    public PageQueriedResult<KycGottenResult> listAuditQueue(String tenantId, String status, int page, int size) {
        log.info("[Kyc-QueryService] 後台發動審核佇列查詢. Tenant: {}, Status: {}, Page: {}, Size: {}",
                tenantId, status, page, size);

        // 可在此處加入額外的唯讀層檢核邏輯（例如校驗狀態字串是否合法）
        return queryRepositoryPort.listByTenantAndStatus(tenantId, status, page, size);
    }
}