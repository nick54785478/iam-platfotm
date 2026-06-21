package com.example.demo.config.init;


import com.example.demo.application.domain.permission.aggregate.PermissionDefinition;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionCode;
import com.example.demo.application.domain.permission.repository.PermissionDefinitionRepository;
import com.example.demo.application.domain.shared.vo.TenantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <h2>[基礎設施層] 本地權限資料初始化器 (Permission Data Initializer)</h2>
 * <p>
 * <b>【職責】</b>：<br>
 * 於系統啟動時執行，負責將 DeptService 專屬的預設權限 (如 ADMIN_ALL, READ_ALL) 註冊至資料庫。<br>
 * 💡 <b>事件觸發機制：</b> 藉由 {@code @Transactional} 的包覆，這裡執行 save() 時產生的 DomainEvent
 * 將無縫觸發 Outbox Handler，自動向 Kafka 廣播，通知 AuthService 註冊這些新權限。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalPermissionDataInitializer implements ApplicationRunner {

    private final PermissionDefinitionRepository permissionRepository;

    // 定義系統預設租戶 (通常這種跨全域的微服務權限，會歸屬於一個特定的 SYSTEM 租戶)
    private static final String SYSTEM_TENANT = "SYSTEM";
    // 預設操作者名稱 (供 Audit Trail 追蹤用)
    private static final String SYSTEM_OPERATOR = "system-initializer";

    /**
     * 內部輔助記錄 (Record)：用來定義預設權限清單
     */
    private record PermissionSeed(String code, String name, String description, String module) {}

    @Override
    @Transactional // 極度重要：保證資料庫存檔與 Outbox 事件寫入在同一個 Transaction 內
    public void run(ApplicationArguments args) {
        log.info(">>> 啟動 [DeptService] 權限資料初始化檢查...");

        TenantId tenantId = new TenantId(SYSTEM_TENANT);

        // 1. 定義 DeptService 轄下的所有初始權限合約
        List<PermissionSeed> seeds = List.of(
                new PermissionSeed("dept-service:ADMIN_ALL", "部門全域管理", "擁有部門管理模組的最高權限 (包含新增、修改、刪除與指派)", "Department"),
                new PermissionSeed("dept-service:READ_ALL",  "部門唯讀檢視", "允許檢視所有部門架構與人員列表，但無法進行任何修改", "Department"),
                new PermissionSeed("dept-service:WRITE",     "部門編輯",    "允許修改既有部門的基本細節與隸屬關係", "Department")
        );

        int addedCount = 0;

        // 2. 遍歷清單，執行等冪性 (Idempotent) 寫入
        for (PermissionSeed seed : seeds) {
            PermissionCode code = new PermissionCode(seed.code());

            // 檢查該權限是否已經在資料庫中了 (防呆)
            if (!permissionRepository.existsByTenantIdAndCode(tenantId, code)) {
                log.info("發現未註冊權限，準備宣告新建: {}", seed.code());

                // 透過聚合根的充血工廠方法宣告 (內部會自動生成 ID 並 raise CreatedEvent)
                PermissionDefinition permission = PermissionDefinition.declare(
                        tenantId,
                        code,
                        seed.name(),
                        seed.description(),
                        seed.module(),
                        SYSTEM_OPERATOR
                );

                // 持久化至本地 DB (觸發 Spring Domain Events -> Outbox Handler -> Kafka)
                permissionRepository.save(permission);
                addedCount++;
            } else {
                log.trace("權限已存在，跳過初始化: {}", seed.code());
            }
        }

        log.info(">>> 權限資料初始化完成。(本次共新增 {} 筆權限)", addedCount);
    }
}