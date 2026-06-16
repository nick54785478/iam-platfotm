package com.example.demo.application.domain.permission.event;


import com.example.demo.application.domain.shared.event.DomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 領域事件：權限定義已宣告建立
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PermissionDefinitionCreatedEvent extends DomainEvent {

    private String permissionId;
    private String code;
    private String name;
    private String description;
    private String module;

    public PermissionDefinitionCreatedEvent(
            String tenantId, String permissionId, String code,
            String name, String description, String module, String operator) {
        super(tenantId, operator);
        this.permissionId = permissionId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.module = module;
    }

    @Override
    public String aggregateType() { return "PermissionDefinition"; }

    @Override
    public String aggregateId() { return this.permissionId; }
}