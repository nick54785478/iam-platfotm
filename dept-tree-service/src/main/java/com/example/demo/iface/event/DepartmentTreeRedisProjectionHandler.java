package com.example.demo.iface.event;

import com.example.demo.application.domain.dept.event.DepartmentCreatedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDeletedEvent;
import com.example.demo.application.domain.dept.event.DepartmentDisabledEvent;
import com.example.demo.application.domain.dept.event.DepartmentMovedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRenamedEvent;
import com.example.demo.application.domain.dept.event.DepartmentRestoredEvent;
import com.example.demo.application.domain.dept.event.DepartmentSortOrderChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    // 架構瘦身：已移除 ReaderPort 與 ObjectMapper，嚴守單一職責原則 (SRP)
    private final StringRedisTemplate redisTemplate;

    // 定義該租戶在 Redis 中的全局 Prefix，方便一次性核彈級清除
    private static final String TENANT_CACHE_PREFIX_PATTERN = "read-model:tenant:%s:*";

    // ==================================================
    // 事件監聽區 (Event Listeners)
    // ==================================================

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentCreatedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門新建，準備刷新視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentMovedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門搬移，準備刷新視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentRenamedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門更名，準備刷新視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentDeletedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門刪除，準備刷新視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentDisabledEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門停用，準備刷新視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentRestoredEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門復原，準備刷新視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentSortOrderChangedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門排序權重變更，準備刷新視圖。租戶: {}", event.getTenantId());
        flushAndRebuildCache(event.getTenantId());
    }

    // ==================================================
    // 🛠️ 核心投影邏輯 (Core Projection Logic)
    // ==================================================

    /**
     * 執行 Redis 視圖的清理
     */
    private void flushAndRebuildCache(String tenantId) {
        try {
            // 執行無死角的核彈級清除
            nukeTenantCaches(tenantId);

            log.info("[CQRS-Redis-Projector] 🟢 租戶 {} 的 Redis 舊視圖已全數成功清除！等待下次查詢自動回填。", tenantId);

        } catch (Exception e) {
            log.error("[CQRS-Redis-Projector] 🔴 Redis 視圖清除失敗！這可能會導致前端讀到髒資料！", e);
        }
    }

    /**
     * 批次清除該租戶底下的所有讀取視圖快取 (包含所有子樹與麵包屑)
     */
    private void nukeTenantCaches(String tenantId) {
        String pattern = String.format(TENANT_CACHE_PREFIX_PATTERN, tenantId);

        // 找出所有匹配 read-model:tenant:{tenantId}:* 的 Keys
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("[CQRS-Redis-Projector] 已核彈級清除 {} 筆相關快取鍵值", keys.size());
        }
    }
}