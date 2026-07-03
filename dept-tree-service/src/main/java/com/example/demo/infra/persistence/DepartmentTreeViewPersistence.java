package com.example.demo.infra.persistence;

import com.example.demo.infra.projection.DepartmentView;
import com.example.demo.infra.shared.dto.DepartmentRootGottenView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentTreeViewPersistence extends JpaRepository<DepartmentView, String> {


    /**
     * <b>【全新升級】動態條件根節點分頁唯讀快查</b>
     * <p>
     * <b>Pragmatic CQRS Boundary Cross 的極致實踐：</b><br>
     * 穿透至唯讀視圖 {@code department_views}。利用強型別介面投影技術，
     * 徹底消滅傳統原生 SQL 依賴陣列下標（如 row[0]）取值的維護地獄。<br>
     * 內建高安全性的「空白字串與 Null 短路防禦機制」，由資料庫優化器直接控制索引掃描計畫。
     * </p>
     *
     * @param tenantId 租戶 ID 業務字串 (第一隔離維度)
     * @param code     篩選代碼 (精準匹配 Equal，可為空)
     * @param name     篩選名稱 (模糊匹配 Like，可為空)
     * @param pageable 內含分頁偏移量與排序策略的基礎設施引數
     * @return 封裝了強型別投影物件的 Spring Data 分頁切片
     */
    @Query(value = """
            SELECT 
                id, 
                code, 
                name, 
                status, 
                sort_order AS sortOrder, 
                direct_headcount AS directHeadcount, 
                total_headcount AS totalHeadcount
            FROM department_views
            WHERE tenant_id = :tenantId
              AND parent_id IS NULL
              AND status != 'DELETED'
              AND (:code IS NULL OR :code = '' OR code = :code)
              AND (:name IS NULL OR :name = '' OR LOWER(name) LIKE LOWER(CONCAT('%', :name, '%')))
            """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM department_views
                    WHERE tenant_id = :tenantId
                      AND parent_id IS NULL
                      AND status != 'DELETED'
                      AND (:code IS NULL OR :code = '' OR code = :code)
                      AND (:name IS NULL OR :name = '' OR LOWER(name) LIKE LOWER(CONCAT('%', :name, '%')))
                    """,
            nativeQuery = true)
    Page<DepartmentRootGottenView> findTenantRootsWithPage(
            @Param("tenantId") String tenantId,
            @Param("code") String code,
            @Param("name") String name,
            Pageable pageable
    );
}
