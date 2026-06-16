package com.example.demo.application.service;

import com.example.demo.application.domain.permission.aggregate.PermissionDefinition;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionCode;
import com.example.demo.application.domain.permission.repository.PermissionDefinitionRepository;
import com.example.demo.application.domain.shared.vo.TenantId;
import com.example.demo.application.shared.command.DefinePermissionCommand;
import com.example.demo.application.shared.command.UpdatePermissionCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <h2>[應用層] 權限定義指令服務 (Command Service)</h2>
 * <p>
 * <b>【架構升級亮點】</b>：<br>
 * 1. <b>強型別防禦：</b> 第一時間將前端傳入的裸字串 (Primitive String) 轉化為 Value Object，將格式錯誤阻絕於領域之外。<br>
 * 2. <b>領域身分自決：</b> 徹底拔除 Application Service 對 ID 生成機制的依賴，將身分賦予的權力還給聚合根。<br>
 * 3. <b>基礎設施解耦：</b> 徹底移除手動寫入 Outbox 的邏輯。Outbox 的觸發全權交由聚合根的 {@code DomainEvent} 與 Spring Event Bus 處理。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionCommandService {

    private final PermissionDefinitionRepository repository;

    /**
     * 宣告 (新增) 一個全新的權限
     *
     * @param tenantIdStr 多租戶識別碼字串
     * @param cmd         包含權限屬性的 Command DTO
     * @param operator    操作者 ID (供 Audit Trail 使用)
     * @return 新生成的權限 ID
     */
    @Transactional
    public String definePermission(String tenantIdStr, DefinePermissionCommand cmd, String operator) {

        // 1. 邊界防禦：轉化為強型別 VO (若格式錯誤，VO 建構子會直接拋出 Exception)
        TenantId tenantId = new TenantId(tenantIdStr);
        PermissionCode code = new PermissionCode(cmd.code());

        // 🌟 亮點：這裡不再負責 new PermissionId()，Application Service 對 ID 結構一無所知

        // 2. 業務防護：檢查是否重複
        if (repository.existsByTenantIdAndCode(tenantId, code)) {
            throw new IllegalArgumentException("Permission code already exists in this tenant: " + code.getValue());
        }

        // 3. 實例化領域實體 (透過充血模型工廠方法，內部已自動生成 ID 並 raise CreatedEvent)
        // 🌟 亮點：不再傳入 id 參數
        PermissionDefinition permission = PermissionDefinition.declare(
                tenantId, code, cmd.name(), cmd.description(), cmd.module(), operator
        );

        // 4. 持久化至本地 DB
        // 💡 魔法發生處：Spring Data JPA 攔截 @DomainEvents 並廣播給 Outbox Handler
        repository.save(permission);

        log.info("[DeptService] 權限 {} 宣告成功，聚合根已存檔並觸發領域事件", code.getValue());

        // 🌟 亮點：從剛誕生並存檔的聚合根身上，把生成的 ID 拿出來回傳給前端
        return permission.getId().getValue();
    }

    /**
     * 更新權限細節 (名稱、描述與所屬模組)
     */
    @Transactional
    public void updatePermissionDetails(String tenantIdStr, String codeStr, UpdatePermissionCommand cmd, String operator) {

        TenantId tenantId = new TenantId(tenantIdStr);
        PermissionCode code = new PermissionCode(codeStr);

        PermissionDefinition permission = repository.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new IllegalArgumentException("Permission not found for code: " + code.getValue()));

        // 呼叫聚合根的業務方法 (內部已自動 raise UpdatedEvent)
        permission.updateDetails(cmd.name(), cmd.description(), cmd.module(), operator);

        // 儲存並觸發事件廣播
        repository.save(permission);

        log.info("[DeptService] 權限 {} 細節更新成功", code.getValue());
    }
}