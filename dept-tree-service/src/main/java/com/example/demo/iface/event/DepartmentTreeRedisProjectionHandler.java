package com.example.demo.iface.event;

import com.example.demo.application.domain.dept.event.*;
import com.example.demo.application.port.DepartmentTreeReaderPort;
import com.example.demo.application.shared.dto.DepartmentNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Set;

/**
 * <h2>[基礎設施層] Redis 讀取模型主動投影器 (CQRS Redis Projector)</h2>
 * <p>
 * <b>【架構定位】</b>：<br>
 * 專責監聽領域層發出的具體業務事件 (如新建、搬移、更名)。
 * 當 SQL Transaction 成功 Commit 後，於背景非同步執行，主動重構並覆寫 Redis 中的視圖 (Read Model)。
 * 完全解耦讀寫模型，落實純粹的 CQRS。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentTreeRedisProjectionHandler {

    private final DepartmentTreeReaderPort readerPort;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 必須與 ReaderAdapter 中的 Key 規則完全一致
    private static final String KEY_SUBTREE = "read-model:tenant:%s:subtree:%s:incl-disabled:%b";
    private static final String KEY_BREADCRUMB_PATTERN = "read-model:tenant:%s:breadcrumb:*";

    // ==================================================
    // 🎧 事件監聽區 (Event Listeners)
    // ==================================================

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentCreatedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門新建，準備刷新視圖。租戶: {}", event.getTenantId());
        rebuildAndProjectToRedis(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentMovedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門搬移，準備刷新視圖。租戶: {}", event.getTenantId());
        rebuildAndProjectToRedis(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentRenamedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門更名，準備刷新視圖。租戶: {}", event.getTenantId());
        rebuildAndProjectToRedis(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentDeletedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門刪除，準備刷新視圖。租戶: {}", event.getTenantId());
        rebuildAndProjectToRedis(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentDisabledEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門停用，準備刷新視圖。租戶: {}", event.getTenantId());
        rebuildAndProjectToRedis(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentRestoredEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門復原，準備刷新視圖。租戶: {}", event.getTenantId());
        rebuildAndProjectToRedis(event.getTenantId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DepartmentSortOrderChangedEvent event) {
        log.info("[CQRS-Redis-Projector] 偵測到部門排序權重變更，準備刷新視圖。租戶: {}", event.getTenantId());
        rebuildAndProjectToRedis(event.getTenantId());
    }

    // ==================================================
    // 🛠️ 核心投影邏輯 (Core Projection Logic)
    // ==================================================

    /**
     * 執行 Redis 視圖的重建與覆寫
     */
    private void rebuildAndProjectToRedis(String tenantId) {
        try {
            // 策略：高頻率的根目錄完整樹 (Root Subtree) 是最常被查詢的，我們主動預熱 (Pre-warm) 它
            // 實務提醒：若系統有多個頂層部門，此處需調整為動態獲取該租戶的所有根節點
            String rootId = "root";

            // 1. 透過 ReaderPort 拉取最新的 SQL 扁平化視圖
            List<DepartmentNode> activeTree = readerPort.getSubtree(tenantId, rootId, false);
            List<DepartmentNode> fullTree = readerPort.getSubtree(tenantId, rootId, true);

            // 2. 序列化為 JSON
            String activeTreeJson = objectMapper.writeValueAsString(activeTree);
            String fullTreeJson = objectMapper.writeValueAsString(fullTree);

            // 3. 覆寫 Redis 鍵值 (無 TTL，由後續變更主動覆蓋)
            redisTemplate.opsForValue().set(String.format(KEY_SUBTREE, tenantId, rootId, false), activeTreeJson);
            redisTemplate.opsForValue().set(String.format(KEY_SUBTREE, tenantId, rootId, true), fullTreeJson);

            // 4. 清除動態零碎快取 (例如特定節點的麵包屑，強制下次查詢時 Fallback DB 並回填)
            clearDynamicCaches(tenantId);

            log.info("[CQRS-Redis-Projector] Redis 視圖刷新成功！租戶: {}", tenantId);

        } catch (Exception e) {
            log.error("[CQRS-Redis-Projector] Redis 視圖刷新失敗！系統將於下次查詢時自動降級依賴 SQL DB", e);
        }
    }

    /**
     * 批次清除無法預熱的動態快取
     */
    private void clearDynamicCaches(String tenantId) {
        String pattern = String.format(KEY_BREADCRUMB_PATTERN, tenantId);
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.debug("[CQRS-Redis-Projector] 已清除 {} 筆動態麵包屑快取", keys.size());
        }
    }
}