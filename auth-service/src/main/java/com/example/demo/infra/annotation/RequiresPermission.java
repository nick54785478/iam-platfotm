package com.example.demo.infra.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <h2>[介面層] 權限校驗宣告註解</h2>
 * <p>
 * 標註於 Controller 的類別或方法上，宣告呼叫該 API 所需的特定系統權限標籤。<br>
 * 此註解的元數據將會在執行期被 {@code PermissionGuardInterceptor} 讀取，
 * 並與 Gateway 下穿的 HTTP Header (X-User-Permissions) 進行降維比對。
 * </p>
 */
@Target({ElementType.METHOD, ElementType.TYPE}) // 允許標註在方法或整個 Controller 類別上
@Retention(RetentionPolicy.RUNTIME)             // 必須在執行期保留，攔截器才能透過反射讀取
public @interface RequiresPermission {

    /**
     * <b>所需的權限代碼字串</b>
     * <p>
     * 建議格式為 {@code systemCode:permissionCode}。
     * 例如: "dept-service:DEPT_CREATE"
     * </p>
     */
    String value();

}