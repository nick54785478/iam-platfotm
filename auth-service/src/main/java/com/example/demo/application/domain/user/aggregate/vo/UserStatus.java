package com.example.demo.application.domain.user.aggregate.vo;

/**
 * <h2>[領域層 - 列舉] 使用者賬號生命週期狀態</h2>
 */
public enum UserStatus {
	/**
	 * 正常活躍狀態，可正常登入、修改資料與鑑權
	 */
	ACTIVE,

	/**
	 * 密碼連續輸入錯誤次數達上限，賬號被系統自動安全鎖定
	 */
	LOCKED,

	/**
	 * 被管理員手動停用（等同於企業內部的軟刪除 Soft-Delete 業務行為）
	 */
	DEACTIVATED
}