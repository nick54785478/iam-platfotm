package com.example.demo.application.domain.shared.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * <h2>[領域層 - 契約] 全局領域事件頂層介面 (Domain Event)</h2>
 * <p>
 * <b>【設計天職】</b>：<br>
 * 本介面為全專案所有領域事件的最高抽象契約。它定義了事件驅動架構（EDA）在進入分布式環境時， 必須攜帶的最小元數據（Metadata）。不論是寫入側的
 * Outbox 收集，還是讀取側、外部微服務的消費，一律面向此契約程式設計。
 * </p>
 */
public interface DomainEvent {

	/**
	 * <b>宇宙唯一事件識別碼 (Event ID)</b>
	 * <p>
	 * <b>【硬核防禦點】</b>：極其重要！專門用於分布式環境下的<b>「消費端去重與冪等性校驗」</b>。 下游消費者（如 Kafka Consumer
	 * 或本地 Projection 處理器）必須拿此 ID 去查去重表，防止網路重試帶來的二次重複消費。
	 * </p>
	 */
	UUID eventId();

	/**
	 * <b>發生該事件的聚合根類型 (Aggregate Type)</b>
	 * <p>
	 * 例如回傳 {@code "USER"} 或 {@code "ROLE"}。 供外圈的 Outbox 引擎或 Kafka Topic
	 * 分流器進行快速分類與歸檔。
	 * </p>
	 */
	String aggregateType();

	/**
	 * <b>發生該事件的聚合根物理主鍵 ID 字串 (Aggregate ID)</b>
	 * <p>
	 * 通常為使用者或角色的 UUID 字串。供下游追蹤「到底是哪一個實體發生了變更」。
	 * </p>
	 */
	String aggregateId();

	/**
	 * <b>事件正式發生的時間點 (Occurred At)</b>
	 * <p>
	 * 記錄業務行為在領域層被引爆的精準時間，用於審計日誌（Audit Log）或消費端的「時序遲到事件排序」。
	 * </p>
	 */
	LocalDateTime occurredAt();
}