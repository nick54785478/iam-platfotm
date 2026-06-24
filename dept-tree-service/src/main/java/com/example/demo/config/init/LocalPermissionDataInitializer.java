package com.example.demo.config.init;


import com.example.demo.application.service.PermissionCommandService;
import com.example.demo.application.shared.command.inbound.DefinePermissionCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * <h2>[基礎設施層] 本地權限資料初始化器 (完全解耦版)</h2>
 * <p>
 * <b>【架構昇華】</b>：<br>
 * 徹底踢除對 Repository 與 Domain 聚合根的直接依賴！<br>
 * 嚴格遵循六角架構原則，全權將資料校驗、持久化與事件驅動流程委託給應用層 {@link PermissionCommandService}。<br>
 * 確保 sys_permissions (宣告型錄) 與 api_resource_rules (門禁標準) 達到 100% 的語意對齊。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPermissionDataInitializer implements ApplicationRunner {

    private final PermissionCommandService permissionCommandService;

    private static final String SYSTEM_TENANT = "SYSTEM";
    private static final String SYSTEM_OPERATOR = "system-initializer";
    private static final String MODULE_DEPT = "Department";

    @Override
    public void run(ApplicationArguments args) {
        log.info(">>> 啟動 [DeptService] 權限安全字典種子資料檢核與自癒流程...");

        // 1. 完整對齊：封裝所有粗粒度 (Coarse-grained) 與細粒度 (Fine-grained) 的權限合約
        List<DefinePermissionCommand> seeds = List.of(
                // 全域粗粒度權限 (適合高階主管)
                new DefinePermissionCommand("dept-service:ADMIN_ALL", "部門全域管理", "擁有部門管理模組的最高權限，可略過細部檢核", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:READ_ALL",  "部門唯讀檢視", "允許檢視所有部門架構與人員列表，但無法修改", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:WRITE",     "部門基礎編輯", "允許修改既有部門的基本細節與隸屬關係", MODULE_DEPT),

                // 細粒度精準權限 (對應 API Resource Rules)
                new DefinePermissionCommand("dept-service:DEPT_CREATE",  "建立組織", "允許建立新的部門節點", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:DEPT_UPDATE",  "更新組織", "允許更名、搬移部門、更改排序", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:DEPT_DELETE",  "刪除組織", "允許邏輯刪除整棵部門樹", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:DEPT_MERGE",   "組織重組", "允許執行高風險的部門合併與資產轉移", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:DEPT_RESTORE", "組織復原", "允許透過時光機復活已刪除的部門", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:DEPT_DISABLE", "停用組織", "允許將部門標記為停用狀態", MODULE_DEPT),

                // 人員編制權限
                new DefinePermissionCommand("dept-service:EMP_ASSIGN",   "指派人員", "允許將員工編制入特定部門", MODULE_DEPT),
                new DefinePermissionCommand("dept-service:EMP_UNASSIGN", "移除人員", "允許將員工從特定部門移出", MODULE_DEPT),

                // 時光機與稽核權限
                new DefinePermissionCommand("dept-service:DEPT_AUDIT",   "時光機稽核", "允許檢視組織變更歷史事件軌跡與快照", MODULE_DEPT)
        );

        // 2. 順暢調用，將等冪防禦與事件發射完好交由 Service 內聚封裝
        for (DefinePermissionCommand command : seeds) {
            try {
                permissionCommandService.definePermission(SYSTEM_TENANT, command, SYSTEM_OPERATOR);
            } catch (Exception e) {
                log.error(">>> 宣告預設種子權限遭遇阻斷! Code: {}", command.code(), e);
            }
        }

        log.info(">>> [DeptService] 權限安全字典與 Outbox 廣播鏈條初始化對齊完畢。");
    }
}