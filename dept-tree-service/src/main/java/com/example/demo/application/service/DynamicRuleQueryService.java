package com.example.demo.application.service;

import com.example.demo.application.port.ApiResourceRuleReaderPort;
import com.example.demo.application.shared.dto.ApiResourceRuleGottenResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * <h2>[應用層] 動態 API 資源權限查詢服務</h2>
 * <p>
 * <b>【核心架構：精確 URI 實體化快取旁路模式 (Cache-Aside)】</b><br>
 * 專司解決 Redis 缺乏 AntPath (萬用字元) 匹配能力之痛點。將前端夾帶動態參數之精確 URI
 * 轉換為 O(1) 命中的 Redis 快取鍵值，實現極致的權限校驗效能與動態規則熱更新。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRuleQueryService {

    private final StringRedisTemplate redisTemplate;

    // 依賴抽象的輸出埠，拒絕直接耦合 JPA/SQL
    private final ApiResourceRuleReaderPort ruleReader;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 預設無權限要求時的標記值，防止「緩存穿透 (Cache Penetration)」攻擊
    private static final String NO_AUTH_REQUIRED = "PUBLIC_ACCESS";

    // Redis Key 命名空間前綴
    private static final String REDIS_KEY_PREFIX = "authz:rule:";

    /**
     * <b>獲取指定請求路徑所需的權限標籤</b>
     *
     * @param httpMethod HTTP 動詞 (e.g., GET, POST)
     * @param requestUri 精確的請求路徑 (e.g., /api/departments/123)
     * @return 該路徑所需的權限標籤，若不需要權限則回傳 null
     */
    public String getRequiredPermission(String httpMethod, String requestUri) {

        // 組合出精確的 Redis Key，例如：authz:rule:GET:/api/departments/123
        String exactRedisKey = REDIS_KEY_PREFIX + httpMethod.toUpperCase() + ":" + requestUri;

        // ===================================================================
        // Step 1: 嘗試從 Redis 極速獲取精確匹配結果 (O(1))
        // ===================================================================
        String cachedPermission = redisTemplate.opsForValue().get(exactRedisKey);

        if (cachedPermission != null) {
            // 若為 PUBLIC_ACCESS 標記，代表明確已知該路徑不需要權限；否則回傳字串本身
            return NO_AUTH_REQUIRED.equals(cachedPermission) ? null : cachedPermission;
        }

        // ===================================================================
        // Step 2: Cache Miss 降級比對邏輯，從 DB 載入「依優先級排序」的全部規則
        // ===================================================================
        List<ApiResourceRuleGottenResult> allRules = ruleReader.findAllActiveRulesSortedByPriority();

        // 預設將其視為公開資源
        String matchedPermission = NO_AUTH_REQUIRED;

        for (ApiResourceRuleGottenResult rule : allRules) {
            // 判斷 HTTP Method 是否相符 (支援 "*" 萬用字元，代表攔截所有動詞)
            boolean methodMatches = "*".equals(rule.httpMethod()) ||
                    rule.httpMethod().equalsIgnoreCase(httpMethod);

            // 判斷 URI 是否相符 (支援 AntPath 萬用字元，如 /api/departments/**)
            if (methodMatches && pathMatcher.match(rule.pathPattern(), requestUri)) {
                matchedPermission = rule.requiredPermissionCode();
                // 因規則已由小到大嚴格排序，命中第一條優先級最高的即刻中斷！
                break;
            }
        }

        // ===================================================================
        // Step 3: 回填快取 (Cache Populate)
        // ===================================================================
        // 將計算好的「精確 URI」與「權限標籤」寫回 Redis，設定 24 小時過期。
        // 未來同一條 URL 進來就能在 Step 1 直接 O(1) 命中。
        redisTemplate.opsForValue().set(exactRedisKey, matchedPermission, Duration.ofHours(24));

        log.debug("[Authz-Cache] 規則未命中，已將 {} {} 解析為 [{}] 並實體化至 Redis。",
                httpMethod, requestUri, matchedPermission);

        return NO_AUTH_REQUIRED.equals(matchedPermission) ? null : matchedPermission;
    }

    /**
     * <b>清空全部動態權限快取 (Cache Eviction)</b>
     * <p>
     * 當管理員在後台新增、修改、刪除或調整規則優先級時，必須呼叫此方法。
     * 下一個請求進來時便會觸發 Cache Miss，重新從 DB 演算出最新的權限並快取。
     * </p>
     */
    public void invalidateAllRulesCache() {
        // 找出所有此微服務的權限規則 Key
        Set<String> keys = redisTemplate.keys(REDIS_KEY_PREFIX + "*");

        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[Authz-Cache] 收到規則異動通知，已成功清除 {} 筆精確 URI 快取。", keys.size());
        }

        /* * 架構師進階備註：
         * 若未來 Redis 內的 key 數量暴增（例如高達百萬級別），
         * 使用 keys() 可能會短暫阻塞 Redis 單執行緒。
         * 屆時可將此處優化為 SCAN 指令，或在 Key 設計上加入版本號 (Version Pattern) 來實現無痛切換。
         * 目前使用 keys() 對於常規系統已相當足夠且穩定。
         */
    }
}