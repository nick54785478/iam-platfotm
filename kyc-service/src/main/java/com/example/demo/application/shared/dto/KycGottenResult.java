package com.example.demo.application.shared.dto;


import java.time.LocalDateTime;

/**
 * <h2>[應用層 - 讀取模型] KYC 列表查詢結果 (唯讀 DTO)</h2>
 * <p>這是一個純 Java record，完全不知道資料庫的存在。</p>
 */
public record KycGottenResult(
        String userId,
        String fullName,
        String maskedNationalId,
        String status,
        String rejectReason,
        LocalDateTime lastUpdatedAt
) {}