package com.example.demo.iface.event;


import com.example.demo.application.shared.event.ApiRuleChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Set;

/**
 * <h2>[介面層/基礎設施層] API 規則快取清理監聽器</h2>
 * <p>
 * 負責監聽內部發佈的 ApiRuleChangedEvent，執行 Redis 快取掃除。
 * 完美隔離 Command 與 Query 的耦合。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiRuleCacheEvictionListener {

    private final StringRedisTemplate redisTemplate;
    private static final String REDIS_KEY_PREFIX = "authz:rule:*";

    /**
     * <b>【核心防護】TransactionPhase.AFTER_COMMIT</b>
     * <p>
     * 這個註解保證了：只有當 Command Service 的 @Transactional 成功 Commit 到資料庫後，
     * 這段清快取的程式碼才會被觸發。如果資料庫 Rollback 了，快取就不會被誤清！
     * </p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRuleChanged(ApiRuleChangedEvent event) {
        log.debug("[Authz-Cache-Listener] 偵測到規則異動事件 (Action: {}, ID: {})，準備清除全域快取...",
                event.action(), event.ruleId());

        Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[Authz-Cache-Listener] 成功清除 {} 筆過期的動態權限快取。", keys.size());
        }
    }
}