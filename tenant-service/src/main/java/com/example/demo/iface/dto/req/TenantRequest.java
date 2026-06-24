package com.example.demo.iface.dto.req;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TenantRequest {

    /**
     * 接收前端傳來的開通請求
     */
    public record ProvisionTenantResource(

            @NotBlank(message = "Tenant ID is required")
            @Size(max = 100)
            String tenantId,

            @NotBlank(message = "Company name is required")
            @Size(max = 100)
            String companyName,

            @NotNull(message = "Plan type is required")
            String planType,

            @NotBlank
            @Email(message = "Invalid root admin email format")
            String adminEmail,

            @NotBlank
            @Size(min = 8, message = "Password must be at least 8 characters")
            String plainPassword
    ) {}

    /**
     * 接收停權原因
     */
    public record SuspendTenantResource(
            @NotBlank(message = "Suspension reason must be provided")
            String reason
    ) {}
}
