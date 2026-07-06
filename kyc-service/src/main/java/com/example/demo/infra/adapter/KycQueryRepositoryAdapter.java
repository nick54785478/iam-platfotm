package com.example.demo.infra.adapter;

import com.example.demo.application.port.KycQueryRepositoryPort;
import com.example.demo.application.shared.dto.KycGottenResult;
import com.example.demo.application.shared.dto.KycPersonalDetailResult;
import com.example.demo.application.shared.dto.PageQueriedResult;
import com.example.demo.infra.projection.repository.KycBackofficeViewRepository;
import com.example.demo.infra.projection.repository.KycPersonalSourceViewRepository;
import com.example.demo.infra.projection.view.KycBackofficeView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * <h2>[基礎設施層 - 適配器] KYC 讀取端關係型資料庫查詢適配器</h2>
 * <p>實作 {@link KycQueryRepositoryPort}。本類別是技術與業務的轉譯邊界：<br>
 * 1. 將內圈的原生 int 參數包裝為 Spring Data 的 Pageable 進行分頁。<br>
 * 2. 透過 Stream 將資料庫視圖 Entity 自動映射轉換為內圈的純淨 Result 物件。</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true) // 唯讀事務優化：提示 JDBC 與 Hibernate 放棄臟檢查 (Dirty Checking)，大幅降低記憶體開銷與查詢延遲
class KycQueryRepositoryAdapter implements KycQueryRepositoryPort {

    private final KycBackofficeViewRepository viewRepository;
    private final KycPersonalSourceViewRepository personalRecordRepository;

    @Override
    public Optional<KycGottenResult> getKycStatus(String tenantId, String userId) {
        return viewRepository.findByTenantIdAndId(tenantId, userId)
                .map(this::toResult);
    }

    /**
     * 獲取本人的完整 KYC 明細 (包含明碼 PII)
     *
     * @param tenantId
     * @param userId
     */
    @Override
    public Optional<KycPersonalDetailResult> getPersonalDetail(String tenantId, String userId) {
        return personalRecordRepository.findByTenantIdAndId(tenantId, userId)
                .map(view -> {
                    // 核心轉譯：安全組裝地址 (避免 Null 污染字串)
                    String fullAddress = String.format("%s%s%s%s",
                            view.getAddrPostalCode() != null ? view.getAddrPostalCode() : "",
                            view.getAddrCity() != null ? view.getAddrCity() : "",
                            view.getAddrState() != null ? view.getAddrState() : "",
                            view.getAddrDetail() != null ? view.getAddrDetail() : ""
                    );

                    return new KycPersonalDetailResult(
                            view.getId(),
                            view.getFirstName(),
                            view.getLastName(),
                            view.getNationalIdNumber(),
                            view.getNationalIdCountry(), // 新增
                            view.getNationalIdType(),
                            view.getDateOfBirth(),
                            fullAddress,                 // 塞入組裝完美的地址
                            view.getStatus(),
                            view.getReviewComments(),
                            view.getReviewedAt() != null ? view.getReviewedAt() : LocalDateTime.now()
                    );
                });
    }

    @Override
    public PageQueriedResult<KycGottenResult> listByTenantAndStatus(String tenantId, String status, int page, int size) {
        // 1. 轉譯分頁參數：預設依據最後更新時間由新到舊 (DESC) 排序，提供後台最佳的營運視角
        Pageable pageable = PageRequest.of(page, size, Sort.by("lastUpdatedAt").descending());

        // 2. 向 JPA View Repository 發動高效能的扁平化查詢
        Page<KycBackofficeView> viewPage = viewRepository.findByTenantIdAndStatus(tenantId, status, pageable);

        // 3. 抹除技術特徵：將 Entity 列表映射轉譯為核心純淨 Result 列表
        List<KycGottenResult> content = viewPage.getContent().stream()
                .map(this::toResult)
                .toList();

        // 4. 組裝為架構無關的 PageQueriedResult 回傳給應用層
        return new PageQueriedResult<>(
                content,
                viewPage.getTotalElements(),
                viewPage.getTotalPages(),
                viewPage.getNumber()
        );
    }

    /**
     * <b>[防腐轉譯] 視圖實體 轉 唯讀事實 Result</b>
     */
    private KycGottenResult toResult(KycBackofficeView view) {
        return new KycGottenResult(
                view.getId(),
                view.getFullName(),
                view.getMaskedNationalId(),
                view.getStatus(),
                view.getRejectReason(),
                view.getLastUpdatedAt()
        );
    }
}