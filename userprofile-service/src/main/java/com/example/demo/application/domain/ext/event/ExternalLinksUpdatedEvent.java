package com.example.demo.application.domain.ext.event;


import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import com.example.demo.application.domain.shared.event.DomainEvent;

public record ExternalLinksUpdatedEvent(
        UUID eventId, String aggregateId, LocalDateTime occurredAt,
        String tenantId, 
        Map<String, String> linksSnapshot, // Key: Platform Name, Value: URL
        String operator, Long version
) implements DomainEvent {

    public ExternalLinksUpdatedEvent(String tenantId, String aggregateId, Map<String, String> linksSnapshot, String operator, Long version) {
        this(UUID.randomUUID(), aggregateId, LocalDateTime.now(), tenantId, linksSnapshot, operator, version);
    }

    @Override public String aggregateType() { return "ExternalLinks"; }
}