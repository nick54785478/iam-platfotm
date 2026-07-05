package com.example.demo.application.domain.pro.event;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.demo.application.domain.shared.event.DomainEvent;

public record ProfessionalProfileUpdatedEvent(UUID eventId, String aggregateId, LocalDateTime occurredAt,
		String tenantId, String jobTitle, String departmentId, String employeeId, String timeZone, String operator,
		Long version) implements DomainEvent {

	public ProfessionalProfileUpdatedEvent(String tenantId, String aggregateId, String jobTitle, String departmentId,
			String employeeId, String timeZone, String operator, Long version) {
		this(UUID.randomUUID(), aggregateId, LocalDateTime.now(), tenantId, jobTitle, departmentId, employeeId,
				timeZone, operator, version);
	}

	@Override
	public String aggregateType() {
		return "ProfessionalProfile";
	}
}