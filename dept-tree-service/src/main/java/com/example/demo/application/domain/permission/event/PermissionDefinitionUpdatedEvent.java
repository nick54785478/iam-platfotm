package com.example.demo.application.domain.permission.event;

import com.example.demo.application.domain.shared.event.DomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 領域事件：權限定義資訊已更新
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionDefinitionUpdatedEvent extends DomainEvent {

    private String permissionId;
    private String code; // 保留 code 方便下游快速對應
    private String name;
    private String description;
    private String module;
    private Long version;

    public PermissionDefinitionUpdatedEvent(
            String tenantId, String permissionId, String code,
            String name, String description, String module,
            String operator, Long version) { // 👈 建構子要求傳入版本號
        super(tenantId, operator);
        this.permissionId = permissionId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.module = module;
        this.version = version; // 👈 賦值
    }

    @Override
    public String aggregateType() { return "PermissionDefinition"; }

    @Override
    public String aggregateId() { return this.permissionId; }
}