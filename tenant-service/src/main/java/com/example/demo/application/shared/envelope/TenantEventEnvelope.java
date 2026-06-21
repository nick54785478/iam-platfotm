package com.example.demo.application.shared.envelope;

import com.example.demo.application.domain.shared.event.DomainEvent;

public record TenantEventEnvelope(String tenantId, DomainEvent event) {
}
