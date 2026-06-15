package com.example.demo.infra.outbox.entity;


import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * <h2>[基礎設施層 - 儲存模型] Outbox 事件持久化實體 (Outbox Event DB Entity)</h2>
 * <p><b>【設計天職】</b>：<br>
 * 本類別對應資料表 {@code outbox_events}。它是整個 SaaS 系統防範「網路中斷」、「分布式不一致」的黃金保險箱。
 * 它老老實實地記錄了聚合根每一次狀態變更的全量 Payload（JSON），是消費端實作分散式去重（Idempotent Consumer）的天然錨點。</p>
 */
@Entity
@Table(name = "outbox_events", indexes = {
		// 🚀 頂規優化：加上專屬表名前綴，完美避開 H2/MySQL 保留字與全局約束命名衝突
		@Index(name = "idx_outbox_events_status_created", columnList = "status, created_at") })
public class OutboxEventDbEntity {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id; // 宇宙唯一事件碼（domainEvent.eventId()），分布式去重的天然防線

	@Column(name = "tenant_id", nullable = false)
	private String tenantId; // 多租戶隔離標籤，支援未來按租戶進行事件審計或大數據清理

	@Column(name = "aggregate_type", nullable = false)
	private String aggregateType; // 聚合根類別，例如: "USER", "ROLE"

	@Column(name = "aggregate_id", nullable = false)
	private String aggregateId; // 聚合根物理識別碼字串

	@Column(name = "event_type", nullable = false)
	private String eventType; // 領域事件類別名稱，例如: "UserChangedEvent"

	@Lob
	@Column(name = "payload", nullable = false, columnDefinition = "TEXT")
	private String payload; // 序列化後的領域事件完全體 JSON 數據

	@Column(name = "status", nullable = false)
	private String status; // PENDING(待發射), PROCESSED(已成功完工), FAILED(發射力竭失敗)

	@Lob
	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage; // 記錄失敗堆疊，方便 Reconciliation Job 線上排查

	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "processed_at")
	private LocalDateTime processedAt;

	/** 🚀 核心升級：JPA 樂觀鎖版本號，捍衛多 Pod 叢集併發排程發送時的安全血統 */
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	protected OutboxEventDbEntity() {
	}

	/**
	 * 業務建構式：建立一筆全新的待發射事件（預設為 PENDING）
	 */
	public OutboxEventDbEntity(UUID id, String tenantId, String aggregateType, String aggregateId, String eventType,
			String payload) {
		this.id = id;
		this.tenantId = tenantId;
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = "PENDING";
		this.createdAt = LocalDateTime.now();
	}

	// ── 充血業務行為方法 ──

	/**
	 * 標記事件已成功發射並結案，清空歷史錯誤
	 */
	public void markAsProcessed() {
		this.status = "PROCESSED";
		this.errorMessage = null;
		this.processedAt = LocalDateTime.now();
	}

	/**
	 * 標記事件處理失敗，留存錯誤日誌，並主動截短防止撐爆資料庫欄位
	 */
	public void markAsFailed(String reason) {
		this.status = "FAILED";
		this.errorMessage = reason != null && reason.length() > 2000 ? reason.substring(0, 2000) : reason;
		this.processedAt = LocalDateTime.now();
	}

	// ── 唯讀 Getters ──
	public UUID getId() {
		return id;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public String getAggregateId() {
		return aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public String getStatus() {
		return status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getProcessedAt() {
		return processedAt;
	}

	public Long getVersion() {
		return version;
	}
}