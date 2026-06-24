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
 * <p>
 * <b>【職責】</b>：於 Spring Boot 應用程式啟動完畢時自動執行。<br>
 * <b>【冪等性防禦】</b>：會先探測資料庫是否已有規則，若為空（如首次部署、重置資料庫），
 * 則自動寫入 Dept Service 核心的 CQRS 與 Temporal API 保護規則，實現開箱即用的零信任安全防禦。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiResourceRuleInitializer implements ApplicationRunner {

    private final ApiResourceRuleReaderPort ruleHandlerPort;
    private final ApiResourceRuleCommandService ruleCommandService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("[System-Init] 開始檢查 API 動態權限規則庫狀態...");

        // 冪等性防禦：如果資料庫已經有啟用的規則，代表已經初始化過或由人工接管，直接跳過
        if (!ruleHandlerPort.findAllActiveRulesSortedByPriority().isEmpty()) {
            log.info("[System-Init] API 規則庫已存在資料，跳過初始化。");
            return;
        }

        log.info("[System-Init] 偵測到空規則庫，準備載入 Dept Service 預設 API 保護矩陣...");

        List<CreateApiRuleCommand> seedRules = List.of(
                // ===================================================================
                // 1. Department Command Controller (寫入端高風險操作 - Priority 10~50)
                // ===================================================================
                // 優先級較高 (10)，因為是精確路徑
                new CreateApiRuleCommand("POST", "/api/departments/tree", "dept-service:DEPT_CREATE", 10),
                new CreateApiRuleCommand("POST", "/api/departments/move", "dept-service:DEPT_UPDATE", 10),

                // 帶有路徑變數的精確操作 (Priority 20)
                new CreateApiRuleCommand("POST", "/api/departments/*/merge", "dept-service:DEPT_MERGE", 20),
                new CreateApiRuleCommand("POST", "/api/departments/*/restore", "dept-service:DEPT_RESTORE", 20),
                new CreateApiRuleCommand("POST", "/api/departments/*/employees", "dept-service:EMP_ASSIGN", 20),
                new CreateApiRuleCommand("DELETE", "/api/departments/*/employees/*", "dept-service:EMP_UNASSIGN", 20),

                // PATCH 屬性更新 (Priority 30)
                new CreateApiRuleCommand("PATCH", "/api/departments/*/name", "dept-service:DEPT_UPDATE", 30),
                new CreateApiRuleCommand("PATCH", "/api/departments/*/disable", "dept-service:DEPT_DISABLE", 30),
                new CreateApiRuleCommand("PATCH", "/api/departments/*/sort-order", "dept-service:DEPT_UPDATE", 30),

                // 基本 CRUD 兜底 (Priority 50)
                new CreateApiRuleCommand("POST", "/api/departments", "dept-service:DEPT_CREATE", 50),
                new CreateApiRuleCommand("DELETE", "/api/departments/*", "dept-service:DEPT_DELETE", 50),

                // ===================================================================
                // 2. Department Temporal Controller (時光機與稽核端 - Priority 60)
                // ===================================================================
                // 這些 API 涉及組織變更軌跡，通常只開放給 HR 主管或系統稽核員 (AUDIT 權限)
                // 使用 ** 來匹配 /api/departments/{tenantId}/{id}/events 這種多層級路徑
                new CreateApiRuleCommand("GET", "/api/departments/**/events", "dept-service:DEPT_AUDIT", 60),
                new CreateApiRuleCommand("GET", "/api/departments/**/state/at", "dept-service:DEPT_AUDIT", 60),
                new CreateApiRuleCommand("GET", "/api/departments/**/state/current", "dept-service:DEPT_AUDIT", 60),

                // ===================================================================
                // 3. Department Query Controller (唯讀端常規操作 - Priority 100)
                // ===================================================================
                // 讀取操作通常給予最廣泛的 DEPT_READ 權限，且 Priority 放最低 (100) 作為 GET 的兜底
                new CreateApiRuleCommand("GET", "/api/departments/*/search", "dept-service:DEPT_READ", 100),
                new CreateApiRuleCommand("GET", "/api/departments/*/hierarchy", "dept-service:DEPT_READ", 100),
                new CreateApiRuleCommand("GET", "/api/departments/**/tree", "dept-service:DEPT_READ", 100),
                new CreateApiRuleCommand("GET", "/api/departments/**/breadcrumbs", "dept-service:DEPT_READ", 100)
        );

        // 批次執行寫入指令
        for (CreateApiRuleCommand cmd : seedRules) {
            ruleCommandService.createRule(cmd);
        }

        log.info("[System-Init] 成功載入 {} 筆預設 API 動態保護規則！", seedRules.size());
    }
}