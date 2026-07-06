package com.example.demo.application.domain.user.aggregate.vo;

/**
 * <b>[枚舉] KYC 審核狀態生命週期</b>
 */
public enum KycStatus {
	UNVERIFIED, // 尚未驗證
	PENDING_REVIEW, // 審核中
	VERIFIED, // 審核通過
	REJECTED // 審核退回
}