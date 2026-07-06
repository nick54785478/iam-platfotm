package com.example.demo.iface.dto.req;

import com.example.demo.application.domain.user.aggregate.vo.NationalId;

/**
 * [Command DTO] 提交 KYC 審查請求
 */
public record SubmitKycResource(
        String firstName,
        String lastName,
        String idNumber,
        String countryCode,
        NationalId.DocumentType documentType,
        String dateOfBirth, // 建議前端傳 ISO 字串 (YYYY-MM-DD)
        
        // 地址資訊
        String addrCountry,
        String addrState,
        String addrCity,
        String addrPostalCode,
        String addrDetail
) {}