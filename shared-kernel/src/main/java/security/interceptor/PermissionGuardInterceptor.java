package security.interceptor;


import lombok.extern.slf4j.Slf4j;
import security.annotation.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import security.service.DynamicRuleQueryService;

import java.util.Arrays;
import java.util.List;


/**
 * <h2>[網關下游] 雙軌聯防全動態權限攔截器 (PermissionGuardInterceptor)</h2>
 * <p>
 * 本元件實作了 Spring WebMVC 的 {@link HandlerInterceptor}，作為進入業務 Controller 前的最後一道防線。
 * 採用「雙軌防禦策略 (Dual-Track Defense)」：
 * <ul>
 * <li><b>軌道一 (Static)</b>：透過 {@link RequiresPermission} 註解，實現編譯期的硬編碼權限控管 (Code-level Governance)。</li>
 * <li><b>軌道二 (Dynamic)</b>：透過 {@link DynamicRuleQueryService} 查詢 Redis/DB，實現運行期的動態路由保護 (Data-driven Governance)。</li>
 * </ul>
 * <b>信任邊界</b>：假設請求已通過 API Gateway 之 JWT 驗證，並已由 Gateway 注入 {@code X-Tenant-Id} 與 {@code X-User-Permissions} 請求頭。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionGuardInterceptor implements HandlerInterceptor {

    private final DynamicRuleQueryService ruleQueryService;

    /**
     * 最高統治權限標籤。擁有此權限者可繞過所有路徑檢查。
     */
    private static final String SUPER_WILDCARD = "*:ADMIN_ALL";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 非 Controller 方法 (如靜態資源請求) 直接放行
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // ===================================================================
        // 防線一：靜態核心硬編碼優先 (Break-Glass Mechanism)
        // 優先讀取 Controller 方法或類別上的註解，用於關鍵路徑的強制防護
        // ===================================================================
        RequiresPermission staticAnnotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (staticAnnotation == null) {
            staticAnnotation = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        }

        String requiredPermission;

        if (staticAnnotation != null) {
            requiredPermission = staticAnnotation.value();
        } else {
            // ===================================================================
            // 防線二：動態查表與 Redis 快取兜底
            // 若無註解，則從「動態規則引擎」獲取該路徑所需的權限代碼
            // ===================================================================
            String tenantId = request.getHeader("X-Tenant-Id");
            String requestUri = request.getRequestURI();
            String httpMethod = request.getMethod();

            // 查詢引擎會自動處理 Redis Cache-Aside 機制，確保 O(1) 效能
            requiredPermission = ruleQueryService.getRequiredPermission(tenantId, httpMethod, requestUri);
        }

        // 若雙軌皆未定義權限要求，則視為該路徑無需特殊權限，預設放行
        if (requiredPermission == null) {
            return true;
        }

        // ===================================================================
        // 權限驗證：解析網關下穿的憑證
        // ===================================================================
        String permissionsHeader = request.getHeader("X-User-Permissions");
        if (permissionsHeader == null || permissionsHeader.isBlank()) {
            return reject(response, "Access Denied: No credentials provided.");
        }

        List<String> userPermissions = Arrays.asList(permissionsHeader.split(","));

        // ===================================================================
        // 權限降維解析引擎 (Wildcard Resolution Engine)
        // ===================================================================

        // 1. 執行權限精確匹配
        boolean isAuthorized = userPermissions.contains(SUPER_WILDCARD) ||
                userPermissions.contains(requiredPermission);

        // 2. 執行模組級別「管理員權限」推導 (Dynamic Role Escalation)
        // 若使用者無該特定功能權限，但擁有該模組的 ADMIN_ALL 權限，則授權通過
        if (!isAuthorized && requiredPermission.contains(":")) {
            // 解析權限格式 (e.g., "dept-service:DEPT_CREATE" -> "dept-service")
            String systemCode = requiredPermission.split(":")[0];
            String localAdminWildcard = systemCode + ":ADMIN_ALL";

            // 檢查是否擁有該子系統的超級統治權
            isAuthorized = userPermissions.contains(localAdminWildcard);
        }

        if (isAuthorized) {
            return true;
        }

        return reject(response, "Access Denied: Requires permission [" + requiredPermission + "]");
    }

    /**
     * 拒絕請求並回傳標準錯誤格式。
     */
    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", msg));
        return false;
    }
}