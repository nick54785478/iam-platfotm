//package com.example.demo.iface.interceptor;
//
//import com.example.demo.infra.annotation.RequiresPermission;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.messaging.handler.HandlerMethod;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.HandlerInterceptor;
//
//import java.util.Arrays;
//import java.util.List;
//
///**
// * <h2>[網關下游] 輕量級分散式權限校驗攔截器</h2>
// * <p>
// * 接收 Gateway 下穿的無狀態 Header，並實作萬用字元 (*:ADMIN_ALL) 的降維解析。
// * </p>
// */
//@Slf4j
//@Component
//public class PermissionGuardInterceptor implements HandlerInterceptor {
//
//    // 定義超級管理員的萬用憑證
//    private static final String SUPER_WILDCARD = "*:ADMIN_ALL";
//
//    // 假設這個微服務的專屬統治憑證是 dept-service:ADMIN_ALL
//    private static final String LOCAL_ADMIN_WILDCARD = "auth-service:ADMIN_ALL";
//
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//
//        // 1. 如果不是呼叫 Controller 方法 (例如呼叫靜態資源)，直接放行
//        if (!(handler instanceof HandlerMethod handlerMethod)) {
//            return true;
//        }
//
//        // 2. 獲取該 API 節點需要的權限標籤 (假設你自訂了 @RequiresPermission 註解)
//        RequiresPermission permissionAnnotation = handlerMethod.getMethodAnnotation(RequiresPermission.class);
//        if (permissionAnnotation == null) {
//            return true; // 該 API 不需要特定權限
//        }
//
//        String requiredPermission = permissionAnnotation.value(); // e.g., "dept-service:DEPT_CREATE"
//
//        // 3. 提取 Gateway 下穿的權限字串
//        String permissionsHeader = request.getHeader("X-User-Permissions");
//        if (permissionsHeader == null || permissionsHeader.isBlank()) {
//            return reject(response, "Access Denied: No permissions found in header.");
//        }
//
//        List<String> userPermissions = Arrays.asList(permissionsHeader.split(","));
//
//        // 4. 🚀 【架構核心：降維解析邏輯】
//        // 檢查順序：全局萬用字元 -> 本地微服務萬用字元 -> 精準權限匹配
//        boolean isAuthorized = userPermissions.contains(SUPER_WILDCARD) ||
//                userPermissions.contains(LOCAL_ADMIN_WILDCARD) ||
//                userPermissions.contains(requiredPermission);
//
//        if (isAuthorized) {
//            return true;
//        }
//
//        return reject(response, "Access Denied: Insufficient privileges.");
//    }
//
//    private boolean reject(HttpServletResponse response, String msg) throws Exception {
//        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
//        response.setContentType("application/json;charset=UTF-8");
//        response.getWriter().write(String.format("{\"error\": \"%s\"}", msg));
//        return false;
//    }
//}