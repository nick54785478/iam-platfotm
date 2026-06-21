package com.example.demo.infra.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.infra.idempotency.ProcessedEvent;

/**
 * Processed Event Persistence (基礎設施層 - 讀取端消費去重持久化介面)
 *
 * <pre>
 * 專責 Idempotent Consumer Pattern (冪等消費者模式) 的唯讀端資料庫安全防護。 
 * 
 * 紀錄所有已經成功處理並投影過的領域事件 ID，防範網路抖動、MQ 重發或全域重播時，造成的讀取端視圖重複加總或計算。
 * </pre>
 */
@Repository
public interface ProcessedEventPersistence extends JpaRepository<ProcessedEvent, String> {

	/**
	 * 原子性排他性去重寫入 (Atomic Idempotent Insert)。
	 * * <pre>
	 * <b>高併發極速防護原理：</b>
	 * * 捨棄「先 SELECT 判定是否存在、再 INSERT」的兩階段低效做法（該做法在高併發下存在 Check-then-Act 的 Race Condition 漏洞）。
	 * 直接利用 PostgreSQL Primary Key 的 <b>唯一性約束 (Unique Constraint)</b> 進行單次原子寫入嘗試。
	 * 若事件 ID 已存在，資料庫會透過 ON CONFLICT 直接優雅忽略該次行為，不回滾也不拋出中斷異常，提供極致的一線防護吞吐量。
	 * </pre>
	 *
	 * @param eventId 領域事件唯一識別碼 (或是附加了 Handler 前綴的複合防護 Key)
	 * @param now     當前處理完成的系統時間
	 * @return 成功寫入的行數 (1 代表此事件為歷史首次處理，放行應用層投影；0 代表歷史已處理過，必須嚴格阻擋攔截)
	 */
	@Modifying
	@Transactional
	@Query(value = """
          INSERT INTO processed_events (event_id, processed_at)
          VALUES (:eventId, :now)
          ON CONFLICT (event_id) DO NOTHING
          """, nativeQuery = true)
	int tryInsertEvent(@Param("eventId") String eventId, @Param("now") Instant now);
}