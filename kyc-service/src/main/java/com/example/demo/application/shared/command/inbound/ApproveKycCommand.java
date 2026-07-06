package com.example.demo.application.shared.command.inbound;

/**
 * <h2>[應用層 - 指令] 核准 KYC 業務指令</h2>
 */
public record ApproveKycCommand(String tenantId, String targetUserId, // 被審核的目標使用者
		String reviewerId // 執行審核動作的管理員
) {
	public ApproveKycCommand {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("TenantId is required");			
		}
		if (targetUserId == null || targetUserId.isBlank()) {			
			throw new IllegalArgumentException("TargetUserId is required");
		}
		if (reviewerId == null || reviewerId.isBlank()) {		
			throw new IllegalArgumentException("ReviewerId is required");
		}
	}
}