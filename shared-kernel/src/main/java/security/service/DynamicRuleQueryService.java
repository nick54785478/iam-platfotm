package security.service;


import security.dto.ApiResourceRuleGottenResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import security.port.ApiResourceRuleQueryRepositoryPort;
import security.port.RuleCacheManagerPort;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * <h2>[應用層] 動態 API 資源權限查詢服務 (支援多租戶覆寫)</h2>
 * <p>
 * <b>【核心架構：精確 URI 實體化快取旁路模式 (Cache-Aside)】</b><br>
 * 專司解決 Redis 缺乏 AntPath (萬用字元) 匹配能力之痛點。將前端夾帶動態參數之精確 URI
 * 轉換為 O(1) 命中的 Redis 快取鍵值，實現極致的權限校驗效能與動態規則熱更新。<br>
 * <b>【SaaS 擴充】</b>：支援「租戶專屬規則覆寫 (Tenant Override)」，優先套用租戶客製化門檻，無則降級為 SYSTEM 預設。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicRuleQueryService {

    private final RuleCacheManagerPort cachePort;

    // 依賴抽象的輸出埠，拒絕直接耦合 JPA/SQL
    private final ApiResourceRuleQueryRepositoryPort ruleReader;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 預設無權限要求時的標記值，防止「緩存穿透 (Cache Penetration)」攻擊
    private static final String NO_AUTH_REQUIRED = "PUBLIC_ACCESS";

    // Redis Key 命名空間前綴
    private static final String REDIS_KEY_PREFIX = "authz:rule:";
    private static final String SYSTEM_TENANT = "SYSTEM";

    /**
     * <b>獲取指定請求路徑所需的權限標籤 (支援租戶專屬覆寫)</b>
     *
     * @param tenantId   當前請求的租戶 ID
     * @param httpMethod HTTP 動詞 (e.g., GET, POST)
     * @param requestUri 精確的請求路徑 (e.g., /api/departments/123)
     * @return 該路徑所需的權限標籤，若不需要權限則回傳 null
     */
    public String getRequiredPermission(String tenantId, String httpMethod, String requestUri) {

        // ===================================================================
        // Step 1: 嘗試從 Redis 極速獲取精確匹配結果 (O(1))
        // 💡 加入 Tenant 維度隔離 (例如: authz:rule:WITS:GET:/api/departments)
        // ===================================================================
        String exactRedisKey = REDIS_KEY_PREFIX + tenantId + ":" + httpMethod.toUpperCase() + ":" + requestUri;

        String cachedPermission = cachePort.get(exactRedisKey);

        if (cachedPermission != null) {
            // 若為 PUBLIC_ACCESS 標記，代表明確已知該路徑不需要權限；否則回傳字串本身
            return NO_AUTH_REQUIRED.equals(cachedPermission) ? null : cachedPermission;
        }

        // ===================================================================
        // Step 2: Cache Miss 降級比對邏輯，從 DB 撈出「該租戶專屬」與「SYSTEM 預設」規則
        // ===================================================================
        List<ApiResourceRuleGottenResult> rules = ruleReader.findRulesForTenantAndSystem(tenantId, SYSTEM_TENANT);

        // 預設將其視為公開資源
        String matchedPermission = NO_AUTH_REQUIRED;

        for (ApiResourceRuleGottenResult rule : rules) {
            // 判斷 HTTP Method 是否相符 (支援 "*" 萬用字元)
            boolean methodMatches = "*".equals(rule.httpMethod()) ||
                    rule.httpMethod().equalsIgnoreCase(httpMethod);

            // 判斷 URI 是否相符 (支援 AntPath 萬用字元)
            if (methodMatches && pathMatcher.match(rule.pathPattern(), requestUri)) {

                // 命中規則！先記錄下來
                matchedPermission = rule.requiredPermissionCode();

                // 💡 優先級覆寫防禦 (Tenant Override Logic)：
                // 如果這條命中規則已經是租戶「專屬」的客製化規則，代表權重最高，直接中斷尋找。
                // 若只是 SYSTEM 級別的通用規則，則先不 break，繼續往下找看看有沒有專屬的覆寫規則。
                if (rule.tenantId().equals(tenantId)) {
                    break;
                }
            }
        }

        // ===================================================================
        // Step 3: 回填快取 (Cache Populate)
        // ===================================================================
        // 將計算好的「精確 URI」與「權限標籤」寫回 Redis，設定 24 小時過期。
        cachePort.set(exactRedisKey, matchedPermission, Duration.ofHours(24));

        log.debug("[Authz-Cache] 規則未命中，已將 [{}] {} {} 解析為 [{}] 並實體化至 Redis。",
                tenantId, httpMethod, requestUri, matchedPermission);

        return NO_AUTH_REQUIRED.equals(matchedPermission) ? null : matchedPermission;
    }

    /**
     * <b>清空全部動態權限快取 (Cache Eviction)</b>
     * <p>
     * 當管理員在後台異動規則時呼叫。由於包含 SYSTEM 規則可能影響所有租戶，
     * 此處直接執行全域大掃除。
     * </p>
     */
    public void invalidateAllRulesCache() {
        cachePort.deleteByPattern(REDIS_KEY_PREFIX + "*");
        log.info("[Authz-Cache] 快取已全數清除。");
    }
}