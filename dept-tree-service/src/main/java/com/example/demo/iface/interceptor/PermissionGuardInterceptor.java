package com.example.demo.iface.interceptor;

import com.example.demo.application.service.DynamicRuleQueryService;
import com.example.demo.infra.annotation.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;


/**
 * <h2>[網關下游] 雙軌聯防全動態權限攔截器 (完全體)</h2>
 */
@Component
@RequiredArgsConstructor
public class PermissionGuardInterceptor implements HandlerInterceptor {

    private final DynamicRuleQueryService ruleQueryService;

    // 定義全局最高統治權憑證
    private static final String SUPER_WILDCARD = "*:ADMIN_ALL";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // ===================================================================
        // 防線一：靜態核心硬編碼優先 (Break-Glass Mechanism)
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
            // 🔄 防線二：動態查表與 Redis 快取兜底
            // ===================================================================
            String requestUri = request.getRequestURI();
            String httpMethod = request.getMethod();
            requiredPermission = ruleQueryService.getRequiredPermission(httpMethod, requestUri);
        }

        if (requiredPermission == null) {
            return true; // 雙軌皆未要求權限，放行
        }

        // 提取網關下穿的權限清單
        String permissionsHeader = request.getHeader("X-User-Permissions");
        if (permissionsHeader == null || permissionsHeader.isBlank()) {
            return reject(response, "Access Denied: No credentials.");
        }

        List<String> userPermissions = Arrays.asList(permissionsHeader.split(","));

        // ===================================================================
        // 權限降維解析引擎 (Wildcard Resolution Engine)
        // ===================================================================

        // 1. 檢查是否擁有宇宙最高權限，或是「精準匹配」了該單一權限
        boolean isAuthorized = userPermissions.contains(SUPER_WILDCARD) ||
                userPermissions.contains(requiredPermission);

        // 2. 【補齊的關鍵防線】：動態推導本地模組最高權限 (Local Admin Wildcard)
        if (!isAuthorized && requiredPermission.contains(":")) {
            // 將 "dept-service:DEPT_CREATE" 切割，取出 "dept-service"
            String systemCode = requiredPermission.split(":")[0];

            // 動態組裝出該子系統的最高權限，如 "dept-service:ADMIN_ALL"
            String localAdminWildcard = systemCode + ":ADMIN_ALL";

            // 檢查使用者是否擁有該子系統的統治權
            isAuthorized = userPermissions.contains(localAdminWildcard);
        }

        if (isAuthorized) {
            return true; // 權限驗證通過，放行
        }

        return reject(response, "Access Denied: Requires " + requiredPermission);
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format("{\"error\": \"%s\"}", msg));
        return false;
    }
}