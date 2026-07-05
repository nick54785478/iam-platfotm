package com.example.demo.application.domain.comunication.event;


import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.application.domain.shared.event.DomainEvent;

public record CommunicationPreferencesUpdatedEvent(
        UUID eventId, String aggregateId, LocalDateTime occurredAt,
        String tenantId, 
        boolean marketingEmail, boolean marketingInApp, boolean marketingSms,
        boolean updatesEmail, boolean updatesInApp, boolean updatesSms,
        String operator, Long version
) implements DomainEvent {

    public CommunicationPreferencesUpdatedEvent(
            String tenantId, String aggregateId,
            boolean mktEmail, boolean mktInApp, boolean mktSms,
            boolean updEmail, boolean updInApp, boolean updSms,
            String operator, Long version) {
        this(UUID.randomUUID(), aggregateId, LocalDateTime.now(),
             tenantId, mktEmail, mktInApp, mktSms, updEmail, updInApp, updSms, operator, version);
    }

    @Override public String aggregateType() { return "CommunicationPreferences"; }
}