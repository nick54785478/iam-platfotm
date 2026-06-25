package security.port;

import java.time.Duration;

/**
 * <h2>[輸出埠] 權限規則快取埠</h2>
 * 定義規則快取所需的操作合約，完全隔離底層技術細節。
 */
public interface RuleCacheManagerPort {
    String get(String key);

    void set(String key, String value, Duration ttl);

    void deleteByPattern(String pattern);
}