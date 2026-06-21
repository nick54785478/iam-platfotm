package com.example.demo.iface.dto.payload;

import java.util.UUID;

public record TenantProvisionedPayload(
        UUID eventId, String tenantId, String companyName,
        String rootAdminEmail, String rootAdminTempPassword, String planType
) {}