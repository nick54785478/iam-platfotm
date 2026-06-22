package com.example.demo.application.port;

import com.example.demo.application.domain.tenant.aggregate.Tenant;
import com.example.demo.application.domain.tenant.aggregate.vo.TenantId;

import java.util.Optional;

/**
 * <h2>[應用層 - 輸出埠] 租戶寫入側持久化介面 (Tenant Writer Port)</h2>
 * <p>
 * 定義租戶資料持久化與領域事件派發的標準合約，全面隔離底層 JPA 技術細節。
 * </p>
 */
public interface TenantStorerPort {

    /**
     * 依據租戶唯一識別碼查詢租戶狀態（還原為充血模型）
     */
    Optional<Tenant> findById(TenantId id);

    /**
     * 儲存或更新租戶聚合根，並自動觸發肚子裡領域事件的清理與廣播
     */
    void save(Tenant tenant);
}