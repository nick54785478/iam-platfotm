package com.example.demo.infra.adapter;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.OutboxEventPublisherPort;
import com.example.demo.application.shared.event.DomainEvent;
import com.example.demo.application.shared.event.TenantEventEnvelope;
import com.example.demo.infra.outbox.entity.OutboxEventDbEntity;

import tools.jackson.databind.ObjectMapper;

@Component
class InMemoryOutboxPublisherAdapter implements OutboxEventPublisherPort {

	private final ApplicationEventPublisher localEventPublisher;
	private final ObjectMapper objectMapper;

	public InMemoryOutboxPublisherAdapter(ApplicationEventPublisher localEventPublisher, ObjectMapper objectMapper) {
		this.localEventPublisher = localEventPublisher;
		this.objectMapper = objectMapper;
	}

	@Override
	public void publish(OutboxEventDbEntity outboxEvent) {
		try {
			// 1. 根據資料庫儲存的事件類別名稱（eventType），利用反射還原其 Class 型態
			// 假設你的事件都在特定 package 下，可以補上全路徑；或直接在 DomainEvent 加上全路徑元數據
			String fullClassName = getFullClassName(outboxEvent.getEventType());
			Class<?> eventClass = Class.forName(fullClassName);

			// 2. 將 JSON 載荷還原為真正的領域事件
			DomainEvent domainEvent = (DomainEvent) objectMapper.readValue(outboxEvent.getPayload(), eventClass);

			// 3. 重新打包進多租戶信封，轟向本地的 Spring 容器！
			// 這會直接精準觸發你的 User/RoleProjectionProcessor 進行本地 View 更新
			localEventPublisher.publishEvent(new TenantEventEnvelope(outboxEvent.getTenantId(), domainEvent));

		} catch (Exception e) {
			throw new RuntimeException("In-Memory outbox redirection failed for event: " + outboxEvent.getId(), e);
		}
	}

	private String getFullClassName(String simpleName) {
		if (simpleName.contains("UserChangedEvent")) {
			return "com.example.demo.application.domain.user.event.UserChangedEvent";
		}
		if (simpleName.contains("RoleChangedEvent")) {
			return "com.example.demo.application.domain.role.event.RoleChangedEvent";
		}
		throw new IllegalArgumentException("Unknown event type: " + simpleName);
	}
}