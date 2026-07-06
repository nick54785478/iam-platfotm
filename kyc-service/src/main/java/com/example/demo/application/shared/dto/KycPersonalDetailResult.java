package com.example.demo.application.shared.dto;

import com.example.demo.application.domain.user.aggregate.vo.NationalId;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <h2>[應用層 - 讀取模型] 個人 KYC 完整明細 (唯讀事實)</h2>
 * <p>專屬於本人的詳細視圖，包含解密後的明碼 PII 資料。</p>
 */
public record KycPersonalDetailResult(
        String userId,
        String firstName,
        String lastName,
        String nationalIdNumber,
        String nationalIdCountry, // Country Code
        NationalId.DocumentType documentType,
        LocalDate dateOfBirth,
        String fullAddress,       // 組合後的完整地址字串
        String status,
        String rejectReason,
        LocalDateTime lastUpdatedAt
) {
}