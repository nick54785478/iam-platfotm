package com.example.demo.iface.exception;

import com.example.demo.application.domain.shared.exception.DomainException;
import org.hibernate.StaleObjectStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全域例外處理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DomainException.class)
	public ResponseEntity<BaseExceptionResponse> handleDomainException(DomainException e) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new BaseExceptionResponse("DOMAIN_EXCEPTION", e.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<BaseExceptionResponse> handleIllegalArgumentException(IllegalArgumentException e) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new BaseExceptionResponse("ILLEGAL_ARGUMENT", e.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<BaseExceptionResponse> handleIllegalStateException(IllegalStateException e) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new BaseExceptionResponse("ILLEGAL_STATUS", e.getMessage()));
	}

	@ExceptionHandler(StaleObjectStateException.class)
	public ResponseEntity<String> handleOptimisticLockingFailure(Exception e) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body("資料已被其他操作更新，請重新整理頁面後再試。");
	}

	/**
	 * 回傳訊息定義
	 */
	record BaseExceptionResponse(String code, String message) {
	}
}
