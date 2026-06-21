package com.example.demo.infra.adapter;

import com.example.demo.application.port.DepartmentRollUpProjectionHandlerPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * <h2>[基礎設施層] Department Roll-Up Projection Adapter</h2>
 * <p>
 * 負責執行部門人數統計滾動加總的具體實作。<br>
 * 🛡️ <b>資料庫極限防禦：</b> 強制採用 {@code GREATEST(..., 0)}，確保在分散式架構下，
 * 即使遭遇幽靈事件重播或併發扣減異常，部門人數的物理底線永遠是 0。
 * </p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DepartmentRollUpProjectionHandlerAdapter implements DepartmentRollUpProjectionHandlerPort {

	private final NamedParameterJdbcTemplate jdbcTemplate;

	@Override
	public void incrementDirectHeadcount(String tenantId, String departmentId, int delta) {
		String sql = """
             UPDATE department_views
             -- 極限防禦：若計算結果小於 0，強制校正為 0
             SET direct_headcount = GREATEST(direct_headcount + :delta, 0)
             WHERE tenant_id = :tenantId AND id = :departmentId
             """;

		jdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("tenantId", tenantId)
				.addValue("departmentId", departmentId)
				.addValue("delta", delta));
	}

	@Override
	public void incrementTotalHeadcountForAncestors(String tenantId, String descendantId, int delta) {
		String sql = """
             UPDATE department_views
             -- 極限防禦：保護整條祖先鏈的總人數不被異常事件扣成負數
             SET total_headcount = GREATEST(total_headcount + :delta, 0)
             WHERE tenant_id = :tenantId
               AND id IN (
                   SELECT ancestor_id
                   FROM department_tree
                   WHERE tenant_id = :tenantId
                     AND descendant_id = :descendantId
               )
             """;

		jdbcTemplate.update(sql, new MapSqlParameterSource()
				.addValue("tenantId", tenantId)
				.addValue("descendantId", descendantId)
				.addValue("delta", delta));
	}
}