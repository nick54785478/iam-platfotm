package com.example.demo.application.service;

import com.example.demo.application.domain.permission.aggregate.PermissionDefinition;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionCode;
import com.example.demo.application.domain.permission.repository.PermissionDefinitionRepository;
import com.example.demo.application.domain.shared.vo.TenantId;
import com.example.demo.application.shared.command.inbound.DefinePermissionCommand;
import com.example.demo.application.shared.command.inbound.UpdatePermissionCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * <h2>[應用層] 權限定義指令服務 (Command Service) - 完全體</h2>
 * <p>
 * 完美貫徹單一入口原則。無論是來自系統啟動時的基礎設施種子資料註冊，亦或是來自平台管理台的 REST API 操作，
 * 全數收攏至本服務進行統一的邊界防禦與領域事件發射。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCommandService {

    private final PermissionDefinitionRepository repository;

    /**
     * <b>【核心業務】自動宣告或等冪覆蓋更新權限字典</b>
     *
     * @param tenantIdStr 多租戶識別碼字串
     * @param cmd         包含權限屬性的 Command DTO
     * @param operator    操作者 ID (供 Audit Trail 使用)
     * @return 權限聚合根唯一識別碼
     */
    @Transactional
    public String definePermission(String tenantIdStr, DefinePermissionCommand cmd, String operator) {

        // 1. 邊界防禦：強型別 VO 轉換，格式錯誤首秒擊落
        TenantId tenantId = new TenantId(tenantIdStr);
        PermissionCode code = new PermissionCode(cmd.code());

        // 2. 業務防護：改採 Optional 探測，實作高度容錯的動態自癒 (Upsert) 語意
        Optional<PermissionDefinition> existingOpt = repository.findByTenantIdAndCode(tenantId, code);

        if (existingOpt.isPresent()) {
            // 【等冪覆蓋】：若權限代碼已存在，視為一次「更名與描述微調上報」
            PermissionDefinition existingPermission = existingOpt.get();
            existingPermission.updateDetails(cmd.name(), cmd.description(), cmd.module(), operator);

            repository.save(existingPermission);
            log.debug("[Permission-Command] 權限 {} 已存在，順暢執行等冪細節更新並廣播 UpdatedEvent", code.getValue());
            return existingPermission.getId().getValue();
        }

        // 3. 全新宣告：透過領域工廠建立滿血實體 (內部自動 raise CreatedEvent)
        PermissionDefinition newPermission = PermissionDefinition.declare(
                tenantId, code, cmd.name(), cmd.description(), cmd.module(), operator
        );

        // 4. 持久化 (無縫驅動 Spring @DomainEvents 寫入本地 Outbox)
        repository.save(newPermission);

        log.info("[Permission-Command] 權限 {} 全新宣告成功，觸發整合事件發射流程", code.getValue());
        return newPermission.getId().getValue();
    }

    /**
     * 手動更新權限細節 (專供 REST API 變更非核心屬性使用)
     */
    @Transactional
    public void updatePermissionDetails(String tenantIdStr, String codeStr, UpdatePermissionCommand cmd, String operator) {
        TenantId tenantId = new TenantId(tenantIdStr);
        PermissionCode code = new PermissionCode(codeStr);

        PermissionDefinition permission = repository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new IllegalArgumentException("找不到指定的權限定義: " + code.getValue()));

        permission.updateDetails(cmd.name(), cmd.description(), cmd.module(), operator);
        repository.save(permission);

        log.info("[Permission-Command] 權限 {} 細節手動變更成功", code.getValue());
    }
}