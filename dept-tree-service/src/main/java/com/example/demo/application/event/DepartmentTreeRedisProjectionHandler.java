package com.example.demo.application.event;

import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDeletedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDisabledEvent;
import com.example.demo.application.domain.dept.event.DepartmentMovedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRenamedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRestoredEvent;
import com.example.demo.application.domain.dept.event.DepartmentSortOrderChangedEvent;
import com.example.demo.application.domain.dept.event.EmployeeAssignedToDepartmentEvent;
import com.example.demo.application.domain.dept.event.EmployeeUnassignedFromDepartmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * <h2>[基礎設施層] Redis 讀取模型主動投影器 (CQRS Redis Projector)</h2>
 * <p>
 * <b>【架構定位】</b>：<br>
 * 專責監聽領域層發出的具體業務事件 (如新建、搬移、更名)。
 * 當 SQL Transaction 成功 Commit 後，於背景非同步執行，主動清除該租戶的舊有 Redis 視圖快取。
 * 清除後，將由前端的下一次查詢觸發 ReaderAdapter 執行 Cache-Aside 懶加載回填，
 * 徹底解決冷啟動與 Root ID 不固定的快取錯位問題。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentTreeRedisProjectionHandler {

    private final StringRedisTemplate redisTemplate;
    private static final String TENANT_CACHE_PREFIX_PATTERN = "read-model:tenant:%s:*";

    // ==================================================
    // 結構幾何變更事件 (維持 @Async 非同步快速清除)
    // ==================================================

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentCreatedEvent event) {
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentMovedEvent event) {
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentRenamedEvent event) {
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentDeletedEvent event) {
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentDisabledEvent event) {
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentRestoredEvent event) {
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentSortOrderChangedEvent event) {
        flushAndRebuildCache(event.getTenantId());
    }

    // ==================================================
    // 人事異動事件 (順序接力：確保 SQL 視圖已更新)
    // ==================================================

    /**
     * 移除 @Async，改用 @Order(2)
     * 確保 Order(1) 的 SQL REQUIRES_NEW 交易 Commit 成功、人數變更可見後，才動手秒殺 Redis
     */
    @Order(2)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EmployeeAssignedToDepartmentEvent event) {
        log.info("[CQRS-Redis-Projector] SQL 滾動更新已就緒，開始清除 Redis 舊視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    @Order(2)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(EmployeeUnassignedFromDepartmentEvent event) {
        log.info("[CQRS-Redis-Projector] SQL 滾動更新已就緒，開始清除 Redis 舊視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    // ==================================================
    // 核心投影清除邏輯
    // ==================================================

    private void flushAndRebuildCache(String tenantId) {
        try {
            String pattern = String.format(TENANT_CACHE_PREFIX_PATTERN, tenantId);
            Set<String> keys = redisTemplate.keys(pattern);

            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("[CQRS-Redis-Projector] 🟢 成功核彈級清除 {} 筆相關快取！等待下次查詢自動回填。租戶: {}", keys.size(), tenantId);
            }
        } catch (Exception e) {
            log.error("[CQRS-Redis-Projector] 🔴 Redis 視圖清除失敗！這可能會導致前端讀到髒資料！", e);
        }
    }
}