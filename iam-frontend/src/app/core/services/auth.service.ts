import { Injectable, signal, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { StorageService } from './storage.service';
import { SystemStorageKey } from '../enums/system-storage.enum';

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export interface GroupRoleInfo {
  groupCode: string;
  groupName: string;
  roleCodes: string[];
}

export interface PermissionDto {
  systemCode: string;
  permissionCode: string;
  permissionName: string;
}

export interface UserPermissionContextRepresentation {
  username: string;
  email: string;
  status: string;
  personalRoles: string[];
  groupRoles: GroupRoleInfo[];
  permissions: PermissionDto[];
  departments?: string[];
}

export interface UserRepresentation {
  id?: string;
  username: string;
  email: string;
  status: string;
  roles: string[];
}

export interface RoleRepresentation {
  id?: string;
  roleCode: string;
  roleName: string;
  permissions: Array<{
    systemCode: string;
    permissionCode: string;
    permissionName: string;
  }>;
}

export interface GroupRepresentation {
  groupCode: string;
  groupName: string;
  memberUserIds?: string[];
  assignedRoleIds?: string[];
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly storageService = inject(StorageService);

  // Angular Signals for reactive state management
  currentUser = signal<string | null>(this.storageService.getLocalStorageItem(SystemStorageKey.USERNAME) || null);
  currentTenant = signal<string | null>(this.storageService.getLocalStorageItem(SystemStorageKey.TENANT) || null);
  userRoles = signal<string[]>([]);
  userPermissions = signal<string[]>([]);

  isAuthenticated = signal<boolean>(!!this.storageService.getLocalStorageItem(SystemStorageKey.JWT_TOKEN));

  login(tenantCode: string, username: string, password: string): Observable<any> {
    return this.http.post<any>(`${environment.apiEndpoint}/auth/login`, { tenantCode, username, password }).pipe(
      tap(res => {
        if (res.token) {
          const token = res.token;
          this.storageService.setLocalStorageItem(SystemStorageKey.JWT_TOKEN, token);
          this.storageService.setLocalStorageItem(SystemStorageKey.TENANT, tenantCode);
          this.storageService.setLocalStorageItem(SystemStorageKey.USERNAME, username);

          this.currentUser.set(username);
          this.currentTenant.set(tenantCode);
          this.isAuthenticated.set(true);

          // After login, load permissions context
          this.loadPermissionsContext(username).subscribe();
        }
      })
    );
  }

  register(tenantCode: string, username: string, password: string, email: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/auth/register`, { tenantCode, username, password, email });
  }

  logout(): void {
    this.storageService.removeLocalStorageItem(SystemStorageKey.JWT_TOKEN);
    this.storageService.removeLocalStorageItem(SystemStorageKey.TENANT);
    this.storageService.removeLocalStorageItem(SystemStorageKey.USERNAME);
    this.currentUser.set(null);
    this.currentTenant.set(null);
    this.userRoles.set([]);
    this.userPermissions.set([]);
    this.isAuthenticated.set(false);
  }

  getUserPermissionsContext(username: string): Observable<ApiResponse<UserPermissionContextRepresentation>> {
    return this.http.get<ApiResponse<UserPermissionContextRepresentation>>(`${environment.apiEndpoint}/users/${username}/permissions-context`);
  }

  loadPermissionsContext(username: string): Observable<ApiResponse<UserPermissionContextRepresentation>> {
    return this.getUserPermissionsContext(username).pipe(
      tap(res => {
        if (res.data) {
          const context = res.data;
          this.userRoles.set(context.personalRoles || []);
          this.userPermissions.set((context.permissions || []).map(p => p.permissionCode));
        }
      })
    );
  }

  // ==========================================
  // ── User Management (UserController) ──────
  // ==========================================

  getUsers(): Observable<ApiResponse<UserRepresentation[]>> {
    return this.http.get<ApiResponse<UserRepresentation[]>>(`${environment.apiEndpoint}/users`);
  }

  getUserDetails(username: string): Observable<ApiResponse<UserRepresentation>> {
    return this.http.get<ApiResponse<UserRepresentation>>(`${environment.apiEndpoint}/users/${username}`);
  }

  createUser(user: { username: string; password?: string; email: string }): Observable<ApiResponse<string>> {
    return this.http.post<ApiResponse<string>>(`${environment.apiEndpoint}/users`, user);
  }

  changePassword(username: string, newPassword: string): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(`${environment.apiEndpoint}/users/${username}/password`, { newPassword });
  }

  updateProfile(username: string, email: string): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(`${environment.apiEndpoint}/users/${username}/profile`, { email });
  }

  deactivateUser(username: string): Observable<ApiResponse<any>> {
    return this.http.delete<ApiResponse<any>>(`${environment.apiEndpoint}/users/${username}`);
  }

  reactivateUser(username: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/users/${username}/reactivate`, {});
  }

  assignRoleToUser(username: string, roleCode: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/users/${username}/roles/${roleCode}`, {});
  }

  // ==========================================
  // ── Role Management (RoleController) ──────
  // ==========================================

  getRoles(): Observable<ApiResponse<RoleRepresentation[]>> {
    return this.http.get<ApiResponse<RoleRepresentation[]>>(`${environment.apiEndpoint}/roles`);
  }

  getRoleDetails(roleCode: string): Observable<ApiResponse<RoleRepresentation>> {
    return this.http.get<ApiResponse<RoleRepresentation>>(`${environment.apiEndpoint}/roles/${roleCode}`);
  }

  createRole(roleName: string, roleCode: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/roles`, { roleName, roleCode });
  }

  renameRole(roleCode: string, newName: string): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(`${environment.apiEndpoint}/roles/${roleCode}/name`, { newName });
  }

  reportPermission(roleCode: string, permission: { systemCode: string, permissionCode: string, permissionName: string }): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/roles/${roleCode}/permissions`, permission);
  }

  // ==========================================
  // ── Group Management (GroupController) ────
  // ==========================================

  getGroups(): Observable<ApiResponse<GroupRepresentation[]>> {
    return this.http.get<ApiResponse<GroupRepresentation[]>>(`${environment.apiEndpoint}/groups`);
  }

  getGroupDetails(groupCode: string): Observable<ApiResponse<GroupRepresentation>> {
    return this.http.get<ApiResponse<GroupRepresentation>>(`${environment.apiEndpoint}/groups/${groupCode}`);
  }

  createGroup(groupName: string, groupCode: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/groups`, { groupName, groupCode });
  }

  renameGroup(groupCode: string, newName: string): Observable<ApiResponse<any>> {
    return this.http.put<ApiResponse<any>>(`${environment.apiEndpoint}/groups/${groupCode}/name`, { newName });
  }

  addMemberToGroup(groupCode: string, username: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/groups/${groupCode}/members/${username}`, {});
  }

  removeMemberFromGroup(groupCode: string, username: string): Observable<ApiResponse<any>> {
    return this.http.delete<ApiResponse<any>>(`${environment.apiEndpoint}/groups/${groupCode}/members/${username}`);
  }

  assignRoleToGroup(groupCode: string, roleCode: string): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${environment.apiEndpoint}/groups/${groupCode}/roles/${roleCode}`, {});
  }

  revokeRoleFromGroup(groupCode: string, roleCode: string): Observable<ApiResponse<any>> {
    return this.http.delete<ApiResponse<any>>(`${environment.apiEndpoint}/groups/${groupCode}/roles/${roleCode}`);
  }

  // ==========================================
  // ── Permission Dictionary Query ───────────
  // ==========================================

  getPermissionsDict(tenantId: string, module?: string, keyword?: string): Observable<ApiResponse<any[]>> {
    let params = new HttpParams();
    if (module) params = params.set('module', module);
    if (keyword) params = params.set('keyword', keyword);
    return this.http.get<ApiResponse<any[]>>(`${environment.apiEndpoint}/permissions/dict`, {
      headers: { 'X-Tenant-Id': tenantId },
      params
    });
  }
}
