package com.example.demo.application.domain.user.aggregate.vo;


/**
 * <h2>[領域層 - 值物件] 賬號核心基礎資訊 (Account Value Object)</h2> *
 * <pre>
 * 完美遵循 DDD 值物件（Value Object）鐵律： 
 * 1. <b>完全不可變 (Immutable)</b>：無任何 Setter 方法，修改內容必須回傳全新物件。<br>
 * 2. <b>內聚防禦性校驗</b>：在緊湊建構式（Compact Constructor）內阻斷任何不合法的髒資料進入領域核心。
 * </pre>
 */
public record AccountInfo(String username, String encryptedPassword, String email) {

	/**
	 * 緊湊建構式：在創建時同步進行強固的邊界條件校驗（Guard Clauses）
	 */
	public AccountInfo{
		if(username==null||username.isBlank()){throw new IllegalArgumentException("Username cannot be empty");
		}

		if(encryptedPassword==null||encryptedPassword.isBlank()){
			throw new IllegalArgumentException("Password cannot be empty");
		}

		if(email==null||!email.contains("@")){
			throw new IllegalArgumentException("Invalid email format");
		}
	}

	/**
	 * <b>【不可變變更行為】更換密碼</b>
	 * <p>
	 * 維持不可變性本質，不修改當前物件屬性，而是組裝並回傳一個全新的 AccountInfo 實體。
	 * </p>
	 * * @param newEncryptedPassword 新的加密密碼雜湊值
	 * 
	 * @return 帶有新密碼的全新 AccountInfo 物件
	 */
	public AccountInfo changePassword(String newEncryptedPassword) {
		return new AccountInfo(this.username, newEncryptedPassword, this.email);
	}
}