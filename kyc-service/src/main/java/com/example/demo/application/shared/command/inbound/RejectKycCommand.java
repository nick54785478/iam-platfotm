package com.example.demo.application.shared.command.inbound;

/**
 * <h2>[應用層 - 指令] 退回 KYC 業務指令</h2>
 */
public record RejectKycCommand(String tenantId, String targetUserId, String reviewerId, String reason // 退回理由
) {
	public RejectKycCommand {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("TenantId is required");
		}
		if (targetUserId == null || targetUserId.isBlank()) {
			throw new IllegalArgumentException("TargetUserId is required");
		}
		if (reviewerId == null || reviewerId.isBlank()) {
			throw new IllegalArgumentException("ReviewerId is required");
		}
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("Reason is required for rejection");
		}
	}
}