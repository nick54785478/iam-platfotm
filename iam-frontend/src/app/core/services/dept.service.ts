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
  activeEmployeeCount: number;
  version: number;
  lastModifiedAt: string;
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
}
