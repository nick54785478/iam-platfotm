import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { StorageService } from '../services/storage.service';
import { SystemStorageKey } from '../enums/system-storage.enum';

/**
 * 這個 tenantHeaderInterceptor 攔截器（Interceptor）會在 Angular 發送每一個 HTTP 請求（Request）到後端之前，攔截該請求並對其進行處理
 * 它主要有兩個攔截點/處理邏輯：
 * 1. 全局攔截：注入 JWT 授權憑證（Authorization Token）
 * 2. 特定 API 攔截：注入 SaaS 租戶（Tenant）與使用者資訊
 */
export const tenantHeaderInterceptor: HttpInterceptorFn = (req, next) => {
  const storageService = inject(StorageService);
  const token = storageService.getLocalStorageItem(SystemStorageKey.JWT_TOKEN);
  const tenantId = storageService.getLocalStorageItem(SystemStorageKey.TENANT) || 'DEFAULT';
  const userId = storageService.getLocalStorageItem(SystemStorageKey.USERNAME) || 'anonymous';

  let clonedReq = req;

  // Set Authorization header if JWT token exists
  if (token) {
    clonedReq = clonedReq.clone({
      headers: clonedReq.headers.set('Authorization', `Bearer ${token}`)
    });
  }

  // Inject SaaS routing and auditing headers for department & tenant APIs
  if (req.url.includes('/api/departments') || req.url.includes('/api/platform/tenants')) {
    clonedReq = clonedReq.clone({
      headers: clonedReq.headers
        .set('X-Tenant-Id', tenantId)
        .set('X-Tenant-ID', tenantId) // Support both casings found in backend
        .set('X-User-Id', userId)
        .set('X-User-ID', userId)
    });
  }

  return next(clonedReq);
};
