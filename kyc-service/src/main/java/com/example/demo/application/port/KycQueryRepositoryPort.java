package com.example.demo.application.port;



import com.example.demo.application.shared.dto.KycGottenResult;
import com.example.demo.application.shared.dto.KycPersonalDetailResult;
import com.example.demo.application.shared.dto.PageQueriedResult;

import java.util.Optional;

/**
 * <h2>[應用層 - 輸出埠] KYC 讀取端專屬查詢庫合約 (KycQueryRepositoryPort)</h2>
 * <p>專職定義唯讀視圖的查詢邊界，完全不允許任何 Spring Data 類型的分頁物件侵入此介面。</p>
 */
public interface KycQueryRepositoryPort {

    /**
     * 獲取單一用戶的 KYC 快取狀態 Result
     */
    Optional<KycGottenResult> getKycStatus(String tenantId, String userId);

    /**
     * 獲取本人的完整 KYC 明細 (包含明碼 PII)
     */
    Optional<KycPersonalDetailResult> getPersonalDetail(String tenantId, String userId);

    /**
     * 依據租戶與審核狀態，分頁查詢扁平化後的 KYC 後台列表
     *
     * @param tenantId 租戶識別碼 (物理隔離防線)
     * @param status   審核狀態 (UNVERIFIED, PENDING_REVIEW, VERIFIED, REJECTED)
     * @param page     當前頁碼 (從 0 開始)
     * @param size     每頁顯示筆數
     */
    PageQueriedResult<KycGottenResult> listByTenantAndStatus(String tenantId, String status, int page, int size);
}