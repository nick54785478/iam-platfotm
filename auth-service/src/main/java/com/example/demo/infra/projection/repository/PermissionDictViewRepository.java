package com.example.demo.infra.projection.repository;

import com.example.demo.infra.projection.view.PermissionDictView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionDictViewRepository extends JpaRepository<PermissionDictView, String> {

    Optional<PermissionDictView> findByTenantIdAndCode(String tenantId, String code);

    /**
     * 動態條件檢索權限字典
     * <p>
     * 💡 <b>效能與動態查詢：</b>
     * 透過 (:module IS NULL OR p.module = :module) 語法，實作 $O(1)$ 的原生動態查詢，
     * 避免在 Java 記憶體中過濾，直接交由資料庫引擎優化執行計畫。
     * </p>
     */
    @Query("""
            SELECT p FROM PermissionDictView p 
            WHERE p.tenantId = :tenantId 
              AND (:module IS NULL OR p.module = :module)
              AND (:keyword IS NULL 
                   OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) 
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY p.module ASC, p.code ASC
            """)
    List<PermissionDictView> searchByCriteria(
            @Param("tenantId") String tenantId,
            @Param("module") String module,
            @Param("keyword") String keyword
    );
}
