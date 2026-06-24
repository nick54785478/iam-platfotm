import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface PagedQueriedView<T> {
  content: T[];
  totalElements: number;
  page: number;
  size: number;
  totalPages: number;
}

export interface TenantSummary {
  tenantId: string;
  companyName: string;
  planType: string;
  status: string;
}

export interface TenantDetail {
  tenantId: string;
  companyName: string;
  planType: string;
  status: string;
  expiryDate?: string;
  // 以下屬性為保留擴充欄位（視後端 Query Model 提供）
  adminEmail?: string;
  suspendedReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class TenantService {
  private readonly http = inject(HttpClient);
  // 統整 API 基礎路徑以對應後端 TenantQueryController (@RequestMapping("/api/v1/platform/tenants"))
  private readonly baseUrl = `${environment.apiEndpoint}/v1/platform/tenants`;
  
  // 統整 API 基礎路徑以對應後端 TenantCommandController (@RequestMapping("/api/platform/tenants"))
  private readonly commandBaseUrl = `${environment.apiEndpoint}/platform/tenants`;

  provisionTenant(tenantId: string, companyName: string, planType: string, adminEmail: string, plainPassword: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(this.commandBaseUrl, {
      tenantId,
      companyName,
      planType,
      adminEmail,
      plainPassword
    });
  }

  suspendTenant(tenantId: string, reason: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.commandBaseUrl}/${tenantId}/suspend`, { reason });
  }

  upgradePlan(tenantId: string, planType: string): Observable<ApiResponse<any>> {
    // Upgrades tenant plan using query parameters as required by backend
    let params = new HttpParams().set('newPlan', planType);
    return this.http.put<ApiResponse<any>>(`${this.commandBaseUrl}/${tenantId}/plan`, {}, { params });
  }

  /**
   * [GET] 根據識別碼單查租戶詳情
   */
  getTenantDetails(tenantId: string): Observable<ApiResponse<TenantDetail>> {
    return this.http.get<ApiResponse<TenantDetail>>(`${this.baseUrl}/${tenantId}`);
  }

  /**
   * [GET] 萬能多條件分頁查詢租戶列表
   */
  searchTenants(companyName?: string, planType?: string, status?: string, page = 0, size = 20): Observable<ApiResponse<PagedQueriedView<TenantSummary>>> {
    let params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());

    if (companyName) {
      params = params.set('companyName', companyName);
    }
    if (planType) {
      params = params.set('planType', planType);
    }
    if (status) {
      params = params.set('status', status);
    }

    return this.http.get<ApiResponse<PagedQueriedView<TenantSummary>>>(this.baseUrl, { params });
  }
}
