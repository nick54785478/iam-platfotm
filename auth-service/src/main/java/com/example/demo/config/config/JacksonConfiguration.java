package com.example.demo.config.config;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson Configuration (基礎設施層 - 全局序列化引擎純手動配置版)
 */
@Slf4j
@Configuration
public class JacksonConfiguration {

	/**
	 * 建構並註冊全局共用的 ObjectMapper 實例。
	 *
	 * @return 配置完畢的 ObjectMapper
	 */
	@Bean
	public ObjectMapper objectMapper() {
		log.info("[Jackson Config] Manually building global ObjectMapper with DomainEventMixIn and JavaTime support.");

		ObjectMapper mapper = new ObjectMapper();

		// 1. 註冊 Java 8 時間模組 (JSR-310)
		// 由於 DomainEvent 中廣泛使用了 Instant 類別，此模組是精準處理現代 Java 時間型別的絕對必需品。
		mapper.registerModule(new JavaTimeModule());

		// 當收到無法識別的新欄位時不會報錯 (增加微服務容錯率)
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		// 2. 關閉將時間序列化為時間戳記 (Timestamps) 的預設行為
		// 強制將時間轉換成人類高可讀性、前端易解析的 ISO-8601 標準字串 (例如: "2026-06-02T15:00:00Z")。
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		return mapper;
	}
}