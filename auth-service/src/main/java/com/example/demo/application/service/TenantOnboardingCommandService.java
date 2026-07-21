package com.example.demo.application.service;

import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.role.aggregate.vo.Permission;
import com.example.demo.application.domain.role.aggregate.vo.RoleId;
import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.port.PasswordEncoderPort;
import com.example.demo.application.port.RoleCommandRepositoryPort;
import com.example.demo.application.port.UserCommandRepositoryPort;
import com.example.demo.infra.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final UserCommandRepositoryPort userWriterPort;
    private final RoleCommandRepositoryPort roleWriterPort;
    private final PasswordEncoderPort passwordEncoder;

    private static final String TENANT_ROOT_ROLE_CODE = "TENANT_ROOT_ADMIN";
    private static final String TENANT_ROOT_ROLE_NAME = "租戶超級管理員";

    @Transactional
    public void initializeTenantRootSecurity(String tenantId, String rootEmail, String plainPassword) {

        TenantContext.setCurrentTenantId(tenantId);

        try {
            // ===================================================================
            // 1. 建立該租戶的超級管理員角色 (Role Aggregate)
            // ===================================================================
            Role rootRole = Role.createCustom(TENANT_ROOT_ROLE_NAME, TENANT_ROOT_ROLE_CODE);

            // 【架構核心】：賦予超級萬用字元權限
            // 系統代碼 (systemCode) = "*", 權限代碼 (permissionCode) = "ADMIN_ALL"
            // 這會在地端資料庫產生一筆 role_permission 紀錄，並在 JWT 裡化作 "*:ADMIN_ALL"
            Permission superAdminPermission = new Permission(
                    "*",                  // 萬用系統代碼
                    "*:ADMIN_ALL",        // 萬用權限代碼
                    "系統最高萬用權限"      // 權限描述名稱 (滿足 Record 的非空防呆)
            );

            // 呼叫聚合根的業務方法
            rootRole.assignPermission(superAdminPermission);
            roleWriterPort.save(rootRole);
            RoleId rootRoleId = rootRole.getId();

            // ===================================================================
            // 2. 處理密碼防禦與使用者建立 (User Aggregate)
            // ===================================================================
            String encryptedPassword = passwordEncoder.encode(plainPassword);
            User rootAdmin = User.createNew(rootEmail, encryptedPassword, rootEmail);

            rootAdmin.assignRole(rootRoleId);

            // ===================================================================
            // 3. CQRS 雙軌制視圖投影擴充
            // ===================================================================
            Set<String> roleCodes = roleWriterPort.findRoleCodesByRoleIds(rootAdmin.getAssignedRoles());
            rootAdmin.confirmRoleAssignmentsForView(roleCodes);
            userWriterPort.save(rootAdmin);

            log.info("[Auth-Application] 成功為租戶 {} 建立 Root Admin 帳號，並掛載萬用字元權限。", tenantId);

        } finally {
            TenantContext.clear();
        }
    }
}