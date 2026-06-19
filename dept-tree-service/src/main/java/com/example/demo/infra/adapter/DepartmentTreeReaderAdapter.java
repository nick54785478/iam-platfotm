package com.example.demo.infra.adapter;

import com.example.demo.application.port.DepartmentTreeReaderPort;
import com.example.demo.application.shared.dto.DepartmentNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Department Query Adapter (Infrastructure Layer)
 *
 * <pre>
 * 專為高效能讀取設計，完美落實純粹 CQRS 的讀取模型 (Read Model)。
 * 1. 極速路徑：優先命中 Redis 預先計算好的 JSON 視圖。
 * 2. 降級路徑：若 Redis Miss 或當機，無縫 Fallback 至原生 SQL 閉包表查詢。
 * (註：遵循純 CQRS 原則，此處即使 Miss 也不主動回填 Redis，回填由專屬的 Projector 負責)
 * </pre>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
class DepartmentTreeReaderAdapter implements DepartmentTreeReaderPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // 定義 Redis Key 的命名空間
    private static final String KEY_SUBTREE = "read-model:tenant:%s:subtree:%s:incl-disabled:%b";
    private static final String KEY_BREADCRUMB = "read-model:tenant:%s:breadcrumb:%s";

    @Override
    public List<DepartmentNode> getSubtree(String tenantId, String rootId, boolean includeDisabled) {
        String redisKey = String.format(KEY_SUBTREE, tenantId, rootId, includeDisabled);

        // 1. 嘗試極速命中 Redis
        try {
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null) {
                log.debug("[CQRS-Query] ⚡ 命中 Redis 讀取模型: Subtree [{}]", rootId);
                return objectMapper.readValue(cachedJson, new TypeReference<List<DepartmentNode>>() {
                });
            }
        } catch (Exception e) {
            log.warn("[CQRS-Query] ⚠️ Redis 讀取失敗，啟動 SQL 降級保護！原因: {}", e.getMessage());
        }

        // 2. Fallback: 執行原生的 SQL 閉包表查詢
        log.info("[CQRS-Query] 🛡️ 穿透至 SQL DB 查詢: Subtree [{}]", rootId);
        StringBuilder sql = new StringBuilder("""
                SELECT
                    v.tenant_id, v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
                    v.direct_headcount, v.total_headcount, dt.depth
                FROM department_tree dt
                JOIN department_views v ON dt.descendant_id = v.id AND dt.tenant_id = v.tenant_id
                WHERE dt.tenant_id = :tenantId
                  AND dt.ancestor_id = :rootId
                  AND v.status != 'DELETED'
                """);

        if (!includeDisabled) {
            sql.append(" AND v.status = 'ACTIVE' ");
        }
        sql.append(" ORDER BY dt.depth ASC, v.sort_order ASC");

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("rootId", rootId);

        List<DepartmentNode> result = jdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> mapRowToNode(rs));

        // 3. 關鍵修復：懶加載回填機制 (Cache-Aside Populate)
        // 既然都花時間查 SQL 了，就把結果回填給 Redis，造福後面的查詢請求！
        if (!result.isEmpty()) {
            try {
                String resultJson = objectMapper.writeValueAsString(result);
                // 針對這種非主動預熱的子查詢，設定 2 小時的 TTL 避免吃光記憶體
                redisTemplate.opsForValue().set(redisKey, resultJson, java.time.Duration.ofHours(2));
                log.info("[CQRS-Query] 已將查詢結果回填至 Redis 讀取模型: Subtree [{}]", rootId);
            } catch (Exception e) {
                log.warn("[CQRS-Query] 嘗試回填 Redis 失敗，但不影響 API 正常運作", e);
            }
        }

        return result;
    }

    @Override
    public List<DepartmentNode> getBreadcrumbPath(String tenantId, String departmentId) {
        String redisKey = String.format(KEY_BREADCRUMB, tenantId, departmentId);

        try {
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null) {
                log.debug("[CQRS-Query] ⚡ 命中 Redis 讀取模型: Breadcrumb [{}]", departmentId);
                return objectMapper.readValue(cachedJson, new TypeReference<List<DepartmentNode>>() {
                });
            }
        } catch (Exception e) {
            log.warn("[CQRS-Query] ⚠️ Redis 讀取失敗，啟動 SQL 降級保護！");
        }

        log.info("[CQRS-Query] 🛡️ 穿透至 SQL DB 查詢: Breadcrumb [{}]", departmentId);
        String sql = """
                SELECT
                    v.tenant_id, v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
                    v.direct_headcount, v.total_headcount, dt.depth
                FROM department_tree dt
                JOIN department_views v ON dt.ancestor_id = v.id AND dt.tenant_id = v.tenant_id
                WHERE dt.tenant_id = :tenantId
                  AND dt.descendant_id = :deptId
                  AND v.status != 'DELETED'
                ORDER BY dt.depth DESC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("deptId", departmentId);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToNode(rs));
    }

    /**
     * 全域關鍵字模糊搜尋
     */
    @Override
    public List<DepartmentNode> searchDepartments(String tenantId, String keyword) {
        String sql = """
                SELECT
                    v.tenant_id,
                    v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
                    v.direct_headcount, v.total_headcount,
                    0 as depth
                FROM department_views v
                WHERE v.tenant_id = :tenantId
                  AND (v.name LIKE :keyword OR v.code LIKE :keyword)
                  AND v.status != 'DELETED' -- 🌟 核心防護：過濾邏輯刪除的幽靈節點
                ORDER BY v.sort_order ASC
                LIMIT 50
                """;

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("keyword",
                "%" + keyword + "%");

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToNode(rs));
    }

    /**
     * 提取共用的 RowMapper 邏輯，保持程式碼 DRY (Don't Repeat Yourself)
     *
     * @param rs 每一列的 SQL 查詢結果集
     * @return 轉換後的 Java DTO (DepartmentNode)
     */
    private DepartmentNode mapRowToNode(ResultSet rs) throws SQLException {
        return new DepartmentNode(
                rs.getString("tenant_id"), rs.getString("id"), rs.getString("parent_id"),
                rs.getString("code"), rs.getString("name"), rs.getString("status"),
                rs.getInt("sort_order"), rs.getInt("depth"), rs.getInt("direct_headcount"),
                rs.getInt("total_headcount")
        );
    }

    @Override
    public Optional<DepartmentNode> findById(String tenantId, String id) {
        String sql = """
                SELECT
                    v.tenant_id, v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
                    v.direct_headcount, v.total_headcount, 0 as depth
                FROM department_views v
                WHERE v.tenant_id = :tenantId
                  AND v.id = :id
                  AND v.status != 'DELETED'
                """;

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("id", id);

        List<DepartmentNode> result = jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToNode(rs));
        return result.stream().findFirst();
    }

    @Override
    public List<DepartmentNode> findDirectChildren(String tenantId, String parentId) {
        String sql = """
                SELECT
                    v.tenant_id, v.id, v.parent_id, v.code, v.name, v.status, v.sort_order,
                    v.direct_headcount, v.total_headcount, 1 as depth
                FROM department_views v
                WHERE v.tenant_id = :tenantId
                  AND v.parent_id = :parentId
                  AND v.status != 'DELETED'
                ORDER BY v.sort_order ASC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("parentId",
                parentId);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> mapRowToNode(rs));
    }

    @Override
    public Map<String, List<String>> findEmployeeMappings(String tenantId, List<String> departmentIds) {
        if (departmentIds == null || departmentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 🌟 命中 idx_view_tenant_department 複合索引，極速批次拉取！
        String sql = """
                SELECT department_id, employee_id
                FROM department_employees_view
                WHERE tenant_id = :tenantId
                  AND department_id IN (:deptIds)
                ORDER BY assigned_at ASC
                """;

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("deptIds",
                departmentIds);

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, List<String>> map = new HashMap<>();
            while (rs.next()) {
                map.computeIfAbsent(rs.getString("department_id"), k -> new ArrayList<>())
                        .add(rs.getString("employee_id"));
            }
            return map;
        });
    }
}