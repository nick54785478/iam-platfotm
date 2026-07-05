import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface DepartmentTreeNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  sortOrder: number;
  status: string;
  directEmployeeCount: number;
  totalEmployeeCount: number;
  children?: DepartmentTreeNode[];
}

export interface DepartmentFlatNode {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  sortOrder: number;
  status: string;
}

export interface DepartmentHierarchy {
  departmentId: string;
  name: string;
  code: string;
  parentId: string | null;
  parentName: string | null;
  children: Array<{ id: string; name: string; code: string; }>;
  employees: string[]; // List of employee IDs assigned to this department
}

export interface TemporalEvent {
  eventId: string;
  tenantId: string;
  aggregateId: string;
  eventType: string;
  occurredAt: string;
  version: number;
  payload: string; // Event data payload string
  operatorId: string;
}

export interface DepartmentTemporalState {
  id: string;
  parentId: string | null;
  code: string;
  name: string;
  status: string;
  activeEmployeeCount?: number;
  version?: number;
  lastModifiedAt?: string;
  lastModifiedBy?: string;
}

@Injectable({
  providedIn: 'root'
})
export class DeptService {
  private http = inject(HttpClient);

  // --- Department Write Commands ---
  createDepartment(id: string, parentId: string | null, code: string, name: string): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments`, { id, parentId, code, name });
  }

  deleteDepartment(departmentId: string): Observable<any> {
    return this.http.delete<any>(`${environment.apiEndpoint}/departments/${departmentId}`);
  }

  moveDepartment(departmentId: string, newParentId: string | null): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments/move`, { departmentId, newParentId });
  }

  assignEmployee(departmentId: string, employeeId: string): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments/${departmentId}/employees`, { employeeId });
  }

  unassignEmployee(departmentId: string, employeeId: string): Observable<any> {
    return this.http.delete<any>(`${environment.apiEndpoint}/departments/${departmentId}/employees/${employeeId}`);
  }

  renameDepartment(id: string, name: string): Observable<any> {
    return this.http.patch<any>(`${environment.apiEndpoint}/departments/${id}/name`, { name });
  }

  disableDepartment(id: string): Observable<any> {
    return this.http.patch<any>(`${environment.apiEndpoint}/departments/${id}/disable`, {});
  }

  changeSortOrder(id: string, sortOrder: number): Observable<any> {
    return this.http.patch<any>(`${environment.apiEndpoint}/departments/${id}/sort-order`, { sortOrder });
  }

  restoreDepartment(departmentId: string): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments/${departmentId}/restore`, {});
  }

  createDepartmentTree(rootNode: any): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments/tree`, rootNode);
  }

  mergeDepartment(sourceDeptId: string, targetDeptId: string): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments/${sourceDeptId}/merge`, { targetDeptId });
  }

  // --- Department Query Commands ---
  getRoots(tenantId: string, page: number = 0, size: number = 10, keyword: string = ''): Observable<any> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (keyword) {
      params = params.set('name', keyword);
    }
    return this.http.get<any>(`${environment.apiEndpoint}/departments/roots`, {
      headers: { 'X-Tenant-Id': tenantId },
      params
    });
  }

  getTree(tenantId: string, rootId: string, includeDisabled = false): Observable<DepartmentTreeNode> {
    const params = new HttpParams().set('includeDisabled', includeDisabled.toString());
    return this.http.get<DepartmentTreeNode>(`${environment.apiEndpoint}/departments/${tenantId}/${rootId}/tree`, { params });
  }

  getBreadcrumbs(tenantId: string, id: string): Observable<any> {
    return this.http.get<any>(`${environment.apiEndpoint}/departments/${tenantId}/${id}/breadcrumbs`);
  }

  searchDepartments(tenantId: string, keyword: string): Observable<any> {
    const params = new HttpParams().set('keyword', keyword);
    return this.http.get<any>(`${environment.apiEndpoint}/departments/${tenantId}/search`, { params });
  }

  getHierarchy(departmentId: string): Observable<any> {
    return this.http.get<any>(`${environment.apiEndpoint}/departments/${departmentId}/hierarchy`);
  }

  getUserDepartmentTree(tenantId: string, employeeId: string): Observable<any> {
    return this.http.get<any>(`${environment.apiEndpoint}/departments/users/${employeeId}/tree`, {
      headers: { 'X-Tenant-Id': tenantId }
    });
  }

  // --- Department Temporal/Time-travel Queries ---
  getEventHistory(tenantId: string, id: string): Observable<any[]> {
    return this.http.get<any[]>(`${environment.apiEndpoint}/departments/${tenantId}/${id}/events`);
  }

  getStateAt(tenantId: string, id: string, timestamp: string): Observable<DepartmentTemporalState> {
    const params = new HttpParams().set('timestamp', timestamp);
    return this.http.get<DepartmentTemporalState>(`${environment.apiEndpoint}/departments/${tenantId}/${id}/state/at`, { params });
  }

  getCurrentState(tenantId: string, id: string): Observable<DepartmentTemporalState> {
    return this.http.get<DepartmentTemporalState>(`${environment.apiEndpoint}/departments/${tenantId}/${id}/state/current`);
  }

  // --- Department Permission Commands ---
  definePermission(tenantId: string, operator: string, payload: { code: string; name: string; description: string; module: string }): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments/permissions`, payload, {
      headers: { 'X-Tenant-Id': tenantId, 'X-User-Id': operator }
    });
  }

  updatePermissionDetails(tenantId: string, operator: string, code: string, payload: { name: string; description: string; module: string }): Observable<any> {
    return this.http.put<any>(`${environment.apiEndpoint}/departments/permissions/${code}`, payload, {
      headers: { 'X-Tenant-Id': tenantId, 'X-User-Id': operator }
    });
  }

  // --- API Resource Rules ---
  createApiRule(tenantId: string, operator: string, payload: { httpMethod: string; pathPattern: string; requiredPermission: string; priority: number }): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/departments/api-rules`, payload, {
      headers: { 'X-Tenant-Id': tenantId, 'X-User-Id': operator }
    });
  }

  updateApiRule(tenantId: string, operator: string, id: number, payload: { httpMethod: string; pathPattern: string; requiredPermission: string; priority: number }): Observable<any> {
    return this.http.put<any>(`${environment.apiEndpoint}/departments/api-rules/${id}`, payload, {
      headers: { 'X-Tenant-Id': tenantId, 'X-User-Id': operator }
    });
  }

  toggleApiRuleStatus(tenantId: string, operator: string, id: number, isActive: boolean): Observable<any> {
    const params = new HttpParams().set('active', isActive.toString());
    return this.http.patch<any>(`${environment.apiEndpoint}/departments/api-rules/${id}/status`, null, {
      headers: { 'X-Tenant-Id': tenantId, 'X-User-Id': operator },
      params
    });
  }

  getPagedApiRules(page: number = 0, size: number = 20, tenantId?: string, httpMethod?: string, pathPattern?: string): Observable<any> {
    let params = new HttpParams().set('page', page.toString()).set('size', size.toString());
    if (tenantId) params = params.set('tenantId', tenantId);
    if (httpMethod) params = params.set('httpMethod', httpMethod);
    if (pathPattern) params = params.set('pathPattern', pathPattern);
    return this.http.get<any>(`${environment.apiEndpoint}/departments/api-rules`, { params });
  }
}
