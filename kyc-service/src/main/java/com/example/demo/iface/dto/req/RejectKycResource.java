package com.example.demo.iface.dto.req;

/**
 * <h2>[介面層 - DTO] 退回 KYC 審查原始請求</h2>
 */
public record RejectKycResource(
        String reason
) {}