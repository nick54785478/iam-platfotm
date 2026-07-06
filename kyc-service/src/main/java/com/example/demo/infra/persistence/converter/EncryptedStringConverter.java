package com.example.demo.infra.persistence.converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * <h2>[基礎設施層] 資料庫欄位加解密轉換器 (JPA Converter)</h2>
 * <p>
 * 利用 Spring Security Crypto 模組，實作可逆的 AES 加解密。<br>
 * 專門用於保護 UserIdentity 實體中的 PII 資料 (身分證、姓名、地址)。<br>
 * 確保領域層(Domain) 處理明文，而資料庫(DB) 落地時一律為密文。
 * </p>
 */
@Component
@Converter // 宣告這是一個 JPA 的轉換器
public class EncryptedStringConverter implements AttributeConverter<String, String> {

	private static TextEncryptor encryptor;

	/**
	 * 靜態注入技巧： JPA Converter 的實例化是由 Hibernate 控制的，所以我們透過 Spring 的 setter 注入，
	 * 將環境變數中的金鑰綁定到靜態變數上，供 Converter 使用。 
	 * 
	 * @param password 你的應用程式加密主密碼 (至少 16 碼以上)
	 * @param salt 鹽值 (必須是 16 進位字串，例如 5c0744940b5c369b)
	 */
	@Value("${app.security.pii.password:DefaultSuperSecretPassword123!}")
	public void setPassword(String password, @Value("${app.security.pii.salt:5c0744940b5c369b}") String salt) {

		// 使用 Spring Security 提供的 AES-256 加密器
		// 注意：Encryptors.text() 預設採用 AES/GCM/NoPadding (帶有隨機 IV)，安全性極高
		// 缺點是同一筆資料每次加密結果不同，無法直接作為 SQL 的 WHERE 條件查詢。
		EncryptedStringConverter.encryptor = Encryptors.text(password, salt);
	}

	/**
	 * Entity 轉 Database：將明文加密為密文後存入 DB
	 */
	@Override
	public String convertToDatabaseColumn(String plainText) {
		if (plainText == null || plainText.isBlank()) {
			return plainText;
		}
		try {
			return encryptor.encrypt(plainText);
		} catch (Exception e) {
			// 系統層級防呆：萬一金鑰設定錯誤導致加密失敗，直接阻擋寫入，防止明文意外落盤
			throw new RuntimeException("Failed to encrypt PII data before saving to database.", e);
		}
	}

	/**
	 * Database 轉 Entity：將 DB 撈出的密文解密為明文供 Domain 層使用
	 */
	@Override
	public String convertToEntityAttribute(String cipherText) {
		if (cipherText == null || cipherText.isBlank()) {
			return cipherText;
		}
		try {
			return encryptor.decrypt(cipherText);
		} catch (Exception e) {
			// 若解密失敗 (例如金鑰被換掉)，拋出異常。
			// 實務上也可以依據需求 return cipherText 或丟出客製化 Exception。
			throw new RuntimeException("Failed to decrypt PII data from database.", e);
		}
	}
}