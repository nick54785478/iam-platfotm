import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { InputSwitchModule } from 'primeng/inputswitch';
import { DropdownModule } from 'primeng/dropdown';
import { TagModule } from 'primeng/tag';
import { AuthService } from '../../../../core/services/auth.service';
import { DeptService } from '../../../../core/services/dept.service';

export interface ApiResourceRuleDto {
  id?: number;
  tenantId?: string;
  httpMethod: string;
  pathPattern: string;
  requiredPermission: string;
  priority: number;
  isActive: boolean;
}

interface PermissionDto {
  id?: string;
  code: string;
  name: string;
  description?: string;
  module: string;
}

@Component({
  selector: 'app-dept-permissions',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    InputTextareaModule,
    InputSwitchModule,
    DropdownModule,
    TagModule
  ],
  templateUrl: './dept-permissions.component.html',
  styleUrl: './dept-permissions.component.scss'
})
export class DeptPermissionsComponent implements OnInit {
  private authService = inject(AuthService);
  private deptService = inject(DeptService);

  permissions = signal<PermissionDto[]>([]);
  searchKeyword = '';

  showModal = signal(false);
  isEditMode = signal(false);

  // Form bound variables
  permCode = '';
  permName = '';
  permDesc = '';
  permModule = 'Department';

  activeTab: 'permissions' | 'api-rules' = 'permissions';

  // --- API Rules State ---
  apiRules = signal<ApiResourceRuleDto[]>([]);
  ruleSearchMethod = '';
  ruleSearchPath = '';
  
  showRuleModal = signal(false);
  isRuleEditMode = signal(false);

  ruleId?: number;
  ruleMethod = 'GET';
  rulePathPattern = '';
  rulePermission = '';
  rulePriority = 10;
  ruleIsActive = true;

  httpMethodOptions = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', '*'];

  ngOnInit() {
    this.loadPermissions();
    this.loadApiRules();
  }

  loadPermissions() {
    const tenantId = this.authService.currentTenant();
    if (!tenantId) return;

    // We pass module 'Department' to filter DeptService permissions
    this.authService.getPermissionsDict(tenantId, 'Department', this.searchKeyword).subscribe({
      next: (res) => {
        this.permissions.set(res.data || []);
      },
      error: (err) => {
        console.error('Failed to load permissions', err);
      }
    });
  }

  onSearch() {
    this.loadPermissions();
  }

  openCreateModal() {
    this.isEditMode.set(false);
    this.permCode = '';
    this.permName = '';
    this.permDesc = '';
    this.permModule = 'Department';
    this.showModal.set(true);
  }

  openEditModal(perm: PermissionDto) {
    this.isEditMode.set(true);
    this.permCode = perm.code;
    this.permName = perm.name;
    this.permDesc = perm.description || '';
    this.permModule = perm.module || 'Department';
    this.showModal.set(true);
  }

  closeModal() {
    this.showModal.set(false);
  }

  onSubmit() {
    const tenantId = this.authService.currentTenant();
    const operator = this.authService.currentUser();
    if (!tenantId || !operator) return;

    const payload = {
      code: this.permCode,
      name: this.permName,
      description: this.permDesc,
      module: this.permModule
    };

    if (this.isEditMode()) {
      this.deptService.updatePermissionDetails(tenantId, operator, this.permCode, payload).subscribe({
        next: () => {
          this.loadPermissions();
          this.closeModal();
        },
        error: (err) => console.error('Failed to update permission', err)
      });
    } else {
      this.deptService.definePermission(tenantId, operator, payload).subscribe({
        next: () => {
          this.loadPermissions();
          this.closeModal();
        },
        error: (err) => console.error('Failed to define permission', err)
      });
    }
  }

  // --- API Rules Methods ---
  loadApiRules() {
    const tenantId = this.authService.currentTenant();
    if (!tenantId) return;

    this.deptService.getPagedApiRules(0, 100, tenantId, this.ruleSearchMethod || undefined, this.ruleSearchPath || undefined).subscribe({
      next: (res) => {
        if (res.data && res.data.content) {
          this.apiRules.set(res.data.content);
        } else if (res.data && Array.isArray(res.data)) {
          this.apiRules.set(res.data);
        } else {
          this.apiRules.set([]);
        }
      },
      error: (err) => console.error('Failed to load api rules', err)
    });
  }

  onRuleSearch() {
    this.loadApiRules();
  }

  openCreateRuleModal() {
    this.isRuleEditMode.set(false);
    this.ruleId = undefined;
    this.ruleMethod = 'GET';
    this.rulePathPattern = '';
    this.rulePermission = '';
    this.rulePriority = 10;
    this.ruleIsActive = true;
    this.showRuleModal.set(true);
  }

  openEditRuleModal(rule: ApiResourceRuleDto) {
    this.isRuleEditMode.set(true);
    this.ruleId = rule.id;
    this.ruleMethod = rule.httpMethod;
    this.rulePathPattern = rule.pathPattern;
    this.rulePermission = rule.requiredPermission;
    this.rulePriority = rule.priority;
    this.ruleIsActive = rule.isActive;
    this.showRuleModal.set(true);
  }

  closeRuleModal() {
    this.showRuleModal.set(false);
  }

  onRuleSubmit() {
    const tenantId = this.authService.currentTenant();
    const operator = this.authService.currentUser();
    if (!tenantId || !operator) return;

    const payload = {
      httpMethod: this.ruleMethod,
      pathPattern: this.rulePathPattern,
      requiredPermission: this.rulePermission,
      priority: this.rulePriority
    };

    if (this.isRuleEditMode() && this.ruleId !== undefined) {
      this.deptService.updateApiRule(tenantId, operator, this.ruleId, payload).subscribe({
        next: () => {
          this.loadApiRules();
          this.closeRuleModal();
        },
        error: (err) => console.error('Failed to update API rule', err)
      });
    } else {
      this.deptService.createApiRule(tenantId, operator, payload).subscribe({
        next: () => {
          this.loadApiRules();
          this.closeRuleModal();
        },
        error: (err) => console.error('Failed to create API rule', err)
      });
    }
  }

  toggleRuleStatus(rule: ApiResourceRuleDto, event: any) {
    const tenantId = this.authService.currentTenant();
    const operator = this.authService.currentUser();
    if (!tenantId || !operator || rule.id === undefined) return;

    const isActive = event.checked;
    this.deptService.toggleApiRuleStatus(tenantId, operator, rule.id, isActive).subscribe({
      next: () => {
        // optimistically updated
      },
      error: (err) => {
        console.error('Failed to toggle API rule status', err);
        this.loadApiRules(); // revert on error
      }
    });
  }
}
