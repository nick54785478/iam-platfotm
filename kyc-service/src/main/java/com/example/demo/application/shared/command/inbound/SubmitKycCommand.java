package com.example.demo.application.shared.command.inbound;

import com.example.demo.application.domain.user.aggregate.vo.NationalId;

import java.time.LocalDate;

/**
 * <h2>[應用層 - 指令] 提交 KYC 業務核心指令 (Immutable Command)</h2>
 * <p>自我包含的強型別物件。在進入應用層服務前，所有防禦校驗（如日期解析）皆已完成，確保核心業務拿到的資料絕對合法。</p>
 */
public record SubmitKycCommand(
        // 核心升級：顯式包含安全上下文，拒絕隱式 ThreadLocal 汙染內圈
        String tenantId,
        String userId,

        String firstName,
        String lastName,
        String idNumber,
        String countryCode,
        NationalId.DocumentType documentType,
        LocalDate dateOfBirth, // 升級為強型別日期

        // 地址資訊
        String addrCountry,
        String addrState,
        String addrCity,
        String addrPostalCode,
        String addrDetail
) {
    public SubmitKycCommand {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("TenantId is required for Command");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserId is required for Command");
        }
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("Date of birth is required for Command");
        }
    }
}