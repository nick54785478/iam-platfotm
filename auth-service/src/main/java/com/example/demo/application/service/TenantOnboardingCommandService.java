package com.example.demo.application.service;

import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.port.PasswordEncoderPort;
import com.example.demo.application.port.RoleWriterPort;
import com.example.demo.application.port.UserWriterPort;
import com.example.demo.infra.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * <h2>[應用層] 租戶入駐安全初始化編排器 (Process Manager)</h2>
 * <p>
 * 負責跨聚合（User, Role）協調，確保新租戶開通時，必定具備一把能正常登入系統的 Root Admin 鑰匙。<br>
 * 嚴格遵循六角架構，僅依賴 Domain Model 與 Outbound Ports，徹底阻斷基礎設施層的技術侵入。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOnboardingCommandService {

    private final UserWriterPort userWriterPort;
    private final RoleWriterPort roleWriterPort;
    private final PasswordEncoderPort passwordEncoder;

    private static final String TENANT_ROOT_ROLE_CODE = "TENANT_ROOT_ADMIN";
    private static final String TENANT_ROOT_ROLE_NAME = "租戶超級管理員";
    private String tenantId;

    /**
     * 執行租戶的初始安全矩陣建立
     *
     * @param tenantId      租戶 ID
     * @param rootEmail     預設管理員信箱 (同時作為 Username)
     * @param plainPassword 預設明碼密碼
     */
    @Transactional
    public void initializeTenantRootSecurity(String tenantId, String rootEmail, String plainPassword) {

        // 護城河防禦：非同步執行緒必須手動初始化多租戶上下文，否則底層 Adapter 將報錯
        TenantContext.setCurrentTenantId(tenantId);

        try {
            // ===================================================================
            // 1. 建立該租戶的超級管理員角色 (Role Aggregate)
            // ===================================================================
            Role rootRole = Role.createCustom(TENANT_ROOT_ROLE_NAME, TENANT_ROOT_ROLE_CODE);

            // 寫入資料庫並自動拔出 RoleChangedEvent 封裝成 TenantEventEnvelope 發射
            roleWriterPort.save(rootRole);
            RoleId rootRoleId = rootRole.getId();

            // ===================================================================
            // 2. 處理密碼防禦與使用者建立 (User Aggregate)
            // ===================================================================
            String encryptedPassword = passwordEncoder.encode(plainPassword);
            User rootAdmin = User.createNew(rootEmail, encryptedPassword, rootEmail);

            // 物理綁定角色 ID
            rootAdmin.assignRole(rootRoleId);

            // ===================================================================
            // 3. CQRS 雙軌制視圖投影擴充 (解決 View 端需要看 RoleCode 的問題)
            // ===================================================================
            // 呼叫 Port 提供的高效能批次翻譯規格，將 UUID 翻譯成人類可讀的 RoleCode
            Set<String> roleCodes = roleWriterPort.findRoleCodesByRoleIds(rootAdmin.getAssignedRoles());

            // 🚀 將完整的 RoleCode 灌回 User，產生完全體的 View Event
            rootAdmin.confirmRoleAssignmentsForView(roleCodes);

            // 寫入資料庫並由 Adapter 統一發射所有 DomainEvent
            userWriterPort.save(rootAdmin);

            log.info("[Auth-Application] 成功為租戶 {} 建立 Root Admin 帳號 ({}) 與角色 ({})。",
                    tenantId, rootEmail, TENANT_ROOT_ROLE_CODE);

        } finally {
            // 極限天坑排除：無論成功或拋出異常，必須清理 ThreadLocal 防止記憶體洩漏與後續請求污染
            TenantContext.clear();
        }
    }
}