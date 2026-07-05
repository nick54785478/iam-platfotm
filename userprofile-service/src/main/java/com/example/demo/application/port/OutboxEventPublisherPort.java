package com.example.demo.application.port;

import com.example.demo.infra.outbox.entity.OutboxEventDbEntity;

/**
 * Outbox 事件發射器接口 (Outbound Port)
 */
public interface OutboxEventPublisherPort {
	
	/**
	 * 將已經持久化完成的 Outbox 實體安全地發射出去
	 */
	void publish(OutboxEventDbEntity outboxEvent);
}