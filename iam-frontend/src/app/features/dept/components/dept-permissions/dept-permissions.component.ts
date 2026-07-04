import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { AuthService } from '../../../../core/services/auth.service';
import { DeptService } from '../../../../core/services/dept.service';

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
    InputTextareaModule
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

  ngOnInit() {
    this.loadPermissions();
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
}
