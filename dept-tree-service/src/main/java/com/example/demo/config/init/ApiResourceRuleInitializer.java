package com.example.demo.config.init;

import com.example.demo.application.port.ApiResourceRuleReaderPort;
import com.example.demo.application.service.ApiResourceRuleCommandService;
import com.example.demo.application.shared.command.inbound.CreateApiRuleCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h2>[基礎設施層] API 動態權限規則種子資料初始化器</h2>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiResourceRuleInitializer implements ApplicationRunner {

    private final ApiResourceRuleReaderPort ruleReaderPort;
    private final ApiResourceRuleCommandService ruleCommandService;

    private static final String SYSTEM_TENANT = "SYSTEM"; // 🚀 定義全域系統租戶

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[System-Init] 開始檢查 API 動態權限規則庫狀態...");

        if (!ruleReaderPort.findAllActiveRulesSortedByPriority().isEmpty()) {
            log.info("[System-Init] API 規則庫已存在資料，跳過初始化。");
            return;
        }

        log.info("[System-Init] 偵測到空規則庫，準備載入 Dept Service 預設 API 保護矩陣...");

        // 🚀 在每一個 Command 的最前面，明確塞入 SYSTEM_TENANT
        List<CreateApiRuleCommand> seedRules = List.of(
                // 1. Department Command Controller
                new CreateApiRuleCommand(SYSTEM_TENANT, "POST", "/api/departments/tree", "dept-service:DEPT_CREATE", 10),
                new CreateApiRuleCommand(SYSTEM_TENANT, "POST", "/api/departments/move", "dept-service:DEPT_UPDATE", 10),

                new CreateApiRuleCommand(SYSTEM_TENANT, "POST", "/api/departments/*/merge", "dept-service:DEPT_MERGE", 20),
                new CreateApiRuleCommand(SYSTEM_TENANT, "POST", "/api/departments/*/restore", "dept-service:DEPT_RESTORE", 20),
                new CreateApiRuleCommand(SYSTEM_TENANT, "POST", "/api/departments/*/employees", "dept-service:EMP_ASSIGN", 20),
                new CreateApiRuleCommand(SYSTEM_TENANT, "DELETE", "/api/departments/*/employees/*", "dept-service:EMP_UNASSIGN", 20),

                new CreateApiRuleCommand(SYSTEM_TENANT, "PATCH", "/api/departments/*/name", "dept-service:DEPT_UPDATE", 30),
                new CreateApiRuleCommand(SYSTEM_TENANT, "PATCH", "/api/departments/*/disable", "dept-service:DEPT_DISABLE", 30),
                new CreateApiRuleCommand(SYSTEM_TENANT, "PATCH", "/api/departments/*/sort-order", "dept-service:DEPT_UPDATE", 30),

                new CreateApiRuleCommand(SYSTEM_TENANT, "POST", "/api/departments", "dept-service:DEPT_CREATE", 50),
                new CreateApiRuleCommand(SYSTEM_TENANT, "DELETE", "/api/departments/*", "dept-service:DEPT_DELETE", 50),

                // 2. Department Temporal Controller
                new CreateApiRuleCommand(SYSTEM_TENANT, "GET", "/api/departments/**/events", "dept-service:DEPT_AUDIT", 60),
                new CreateApiRuleCommand(SYSTEM_TENANT, "GET", "/api/departments/**/state/at", "dept-service:DEPT_AUDIT", 60),
                new CreateApiRuleCommand(SYSTEM_TENANT, "GET", "/api/departments/**/state/current", "dept-service:DEPT_AUDIT", 60),

                // 3. Department Query Controller
                new CreateApiRuleCommand(SYSTEM_TENANT, "GET", "/api/departments/*/search", "dept-service:READ_ALL", 100),
                new CreateApiRuleCommand(SYSTEM_TENANT, "GET", "/api/departments/*/hierarchy", "dept-service:READ_ALL", 100),
                new CreateApiRuleCommand(SYSTEM_TENANT, "GET", "/api/departments/**/tree", "dept-service:READ_ALL", 100),
                new CreateApiRuleCommand(SYSTEM_TENANT, "GET", "/api/departments/**/breadcrumbs", "dept-service:READ_ALL", 100)
        );

        for (CreateApiRuleCommand cmd : seedRules) {
            ruleCommandService.createRule(cmd);
        }

        log.info("[System-Init] 成功載入 {} 筆預設 API 動態保護規則！", seedRules.size());
    }
}