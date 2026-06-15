package com.example.demo.application.port;

import java.util.List;
import java.util.Optional;

import com.example.demo.application.shared.dto.GroupRepresentation;

/**
 * <h2>[應用層 - Port 接口] 群組讀取側輸出端口 (Group Reader Port)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本介面為六角形架構的 Outbound Port。它定義了應用層查詢側（Query Side）拉取群組快照數據的最低規格。 讓上層的
 * {@code GroupQueryService} 徹底與持久化框架（如 Spring Data JPA）解耦。
 * </p>
 */
public interface GroupReaderPort {

	/**
	 * 規格對齊：傳入當前多租戶標籤與業務主角 groupCode 進行快照查詢
	 */
	Optional<GroupRepresentation> fetchByGroupCode(String tenantId, String groupCode);

	/**
	 * 規格對齊：傳入當前多租戶標籤，獲取該租戶空間下的全量群組視圖清單
	 */
	List<GroupRepresentation> fetchAllByTenant(String tenantId);
}