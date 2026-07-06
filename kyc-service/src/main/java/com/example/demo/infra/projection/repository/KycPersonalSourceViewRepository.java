package com.example.demo.infra.projection.repository;

import com.example.demo.infra.persistence.entity.UserIdentityEntity;
import com.example.demo.infra.projection.view.KycPersonalSourceView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * <h2>[基礎設施層 - 查詢庫] 源頭表絕對唯讀庫</h2>
 * <p>
 * 只繼承核心的 Repository，不繼承 CrudRepository/JpaRepository。
 * 這樣這個 Interface 就只有讀取能力，徹底防止 Query 端程式碼污染寫入狀態。
 * </p>
 */
@Repository
public interface KycPersonalSourceViewRepository extends JpaRepository<KycPersonalSourceView, String> {

    // 只能定義 Select 查詢，獲取源頭加密的完整 Entity
    Optional<KycPersonalSourceView> findByTenantIdAndId(String tenantId, String id);
}