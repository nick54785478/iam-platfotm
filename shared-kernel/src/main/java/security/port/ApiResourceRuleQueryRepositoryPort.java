package security.port;


import security.dto.ApiResourceRuleGottenResult;

import java.util.List;

/**
 * <h2>API 規則庫讀取合約 (Read Model)</h2>
 * <p>
 * 遵循 ISP 原則，專供查詢端 (Query Service) 使用，阻斷任何修改資料的可能性。
 * </p>
 */
public interface ApiResourceRuleQueryRepositoryPort {

    List<ApiResourceRuleGottenResult> findRulesForTenantAndSystem(String tenantId, String systemTenant);

}