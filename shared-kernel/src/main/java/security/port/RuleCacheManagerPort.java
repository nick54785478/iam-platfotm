package security.port;

import java.time.Duration;


/**
 * <h2>[輸出埠] 規則快取管理介面 (RuleCacheManagerPort)</h2>
 * <p>
 * 定義動態權限規則的快取存取合約。此埠將「業務服務」與「具體的快取技術（如 Redis, Caffeine, Memcached）」進行物理隔離。
 * </p>
 * * <p>
 * <b>【實作責任說明】 (Implementation Responsibility)</b>：<br>
 * 本介面為「輸出埠 (Output Port)」，定義於 Shared Kernel 核心領域。<b>絕對不可在此處引入 Redis 或其他技術框架實作</b>。<br>
 * 各微服務 (e.g., DeptService, AuthService) 必須於各自的基礎設施層 (Infrastructure Layer)
 * 實作具體的 Adapter (e.g., {@code RedisRuleCacheAdapter}) 來滿足此合約。<br>
 * <b>目的</b>：達成「核心與技術細節解耦」，確保業務邏輯在切換快取技術時無需異動。
 * </p>
 * * <p>
 * 本介面底層實作必須確保快取的一致性與 TTL 機制，以防資料陳舊攻擊 (Stale Data Attack)。
 * </p>
 */
public interface RuleCacheManagerPort {

    /**
     * 根據指定鍵值獲取快取內容。
     *
     * @param key 快取鍵值 (應遵守統一命名規範，例如: {@code authz:rule:{tenantId}:{method}:{path}})
     * @return 若快取存在則回傳對應的權限標籤；若不存在 (Cache Miss) 則回傳 {@code null}
     */
    String get(String key);

    /**
     * 將資料寫入快取，並設置存活時間 (TTL)。
     * <p>
     * <b>重要約定</b>：為了保證分散式系統的資料最終一致性，所有寫入行為必須包含有效的 TTL，
     * 嚴禁寫入永不過期的資料，以避免記憶體溢出風險。
     * </p>
     *
     * @param key   快取鍵值
     * @param value 欲寫入的權限標籤內容
     * @param ttl   存活時間 (Time-To-Live)，過期後資料應自動被移除
     */
    void set(String key, String value, Duration ttl);

    /**
     * 根據模式批次刪除快取。
     * <p>
     * 當系統發生規則異動 (如新增、修改、停用規則) 時，負責執行 Cache Eviction，
     * 確保各服務能即時反應最新的安全策略，避免髒資料導致權限判斷錯誤。
     * </p>
     *
     * @param pattern 匹配模式 (如: {@code "authz:rule:*" }，具體匹配行為取決於實作技術，如 Redis key pattern)
     */
    void deleteByPattern(String pattern);
}