package com.example.demo.application.port;

/**
 * 密碼加密規格接口 (Domain Port)
 */
public interface PasswordEncoderPort {
	/**
	 * 將明碼密碼加密成雜湊碼 (Hash)
	 */
	String encode(String rawPassword);

	/**
	 * 驗證明碼與加密後的密碼是否相符
	 */
	boolean matches(String rawPassword, String encodedPassword);
}