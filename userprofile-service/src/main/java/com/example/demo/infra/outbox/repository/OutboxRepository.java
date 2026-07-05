package com.example.demo.infra.outbox.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.outbox.entity.OutboxEventDbEntity;

/**
 * <h2>[基礎設施層 - 倉庫] Outbox 基礎設施資料存取接口</h2>
 */
@Repository
public interface OutboxRepository extends JpaRepository<OutboxEventDbEntity, UUID> {

	/**
	 * 供 OutboxExporter 呼叫：依據創建時間升序（FIFO 先進先出原則），撈出最老的一批待發射事件
	 * 
	 * @param status 狀態過濾欄位，一律傳入 "PENDING"
	 */
	List<OutboxEventDbEntity> findTop20ByStatusOrderByCreatedAtAsc(String status);
}