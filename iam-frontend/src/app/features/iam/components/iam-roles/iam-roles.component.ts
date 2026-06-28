import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, RoleRepresentation } from '../../../../core/services/auth.service';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';

@Component({
  selector: 'app-iam-roles',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule
  ],
  templateUrl: './iam-roles.component.html',
  styleUrl: './iam-roles.component.scss'
})
export class IamRolesComponent implements OnInit {
  private readonly authService = inject(AuthService);

  roles = signal<RoleRepresentation[]>([]);

  // Role form
  showAddRoleModal = signal(false);
  newRoleCode = '';
  newRoleName = '';

  // Permission form
  permRoleTarget = signal<RoleRepresentation | null>(null);
  newPermSys = 'DEPT_SERVICE';
  newPermCode = '';
  newPermName = '';

  // Rename role
  renameRoleTarget = signal<RoleRepresentation | null>(null);
  newRoleRename = '';

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.authService.getRoles().subscribe({
      next: (res) => this.roles.set(res?.data || []),
      error: () => this.roles.set(this.mockRoles())
    });
  }

  // --- Roles Handlers ---
  openAddRoleModal(): void {
    this.showAddRoleModal.set(true);
    this.newRoleCode = '';
    this.newRoleName = '';
  }

  onCreateRole(): void {
    this.authService.createRole(this.newRoleName, this.newRoleCode).subscribe({
      next: () => {
        this.loadRoles();
        this.closeModals();
      },
      error: () => {
        this.roles.set([...this.roles(), {
          roleCode: this.newRoleCode.toUpperCase(),
          roleName: this.newRoleName,
          permissions: []
        }]);
        this.closeModals();
      }
    });
  }

  openAssignPermModal(role: RoleRepresentation): void {
    this.permRoleTarget.set(role);
    this.newPermSys = 'DEPT_SERVICE';
    this.newPermCode = '';
    this.newPermName = '';
  }

  onAssignPermissionSubmit(): void {
    const target = this.permRoleTarget();
    if (target) {
      const permObj = {
        systemCode: this.newPermSys,
        permissionCode: this.newPermCode.toUpperCase(),
        permissionName: this.newPermName
      };
      this.authService.reportPermission(target.roleCode, permObj).subscribe({
        next: () => {
          this.loadRoles();
          this.closeModals();
        },
        error: () => {
          this.roles.set(this.roles().map(r => {
            if (r.roleCode === target.roleCode) {
              return { ...r, permissions: [...r.permissions, permObj] };
            }
            return r;
          }));
          this.closeModals();
        }
      });
    }
  }

  openRenameRoleModal(role: RoleRepresentation): void {
    this.renameRoleTarget.set(role);
    this.newRoleRename = role.roleName;
  }

  onRenameRoleSubmit(): void {
    const target = this.renameRoleTarget();
    if (target && this.newRoleRename) {
      this.authService.renameRole(target.roleCode, this.newRoleRename).subscribe({
        next: () => {
          this.loadRoles();
          this.closeModals();
        },
        error: () => {
          this.roles.set(this.roles().map(r =>
            r.roleCode === target.roleCode ? { ...r, roleName: this.newRoleRename } : r
          ));
          this.closeModals();
        }
      });
    }
  }

  closeModals(): void {
    this.showAddRoleModal.set(false);
    this.permRoleTarget.set(null);
    this.renameRoleTarget.set(null);
  }

  private mockRoles(): RoleRepresentation[] {
    return [
      {
        roleCode: 'ADMIN',
        roleName: 'Tenant Enterprise Administrator',
        permissions: [
          { systemCode: 'DEPT_SERVICE', permissionCode: 'DEPT_WRITE', permissionName: 'Modify Organization chart' },
          { systemCode: 'AUTH_SERVICE', permissionCode: 'USER_WRITE', permissionName: 'Administer Tenant Users' }
        ]
      },
      {
        roleCode: 'DEVELOPER',
        roleName: 'System Core Developer',
        permissions: [
          { systemCode: 'DEPT_SERVICE', permissionCode: 'DEPT_READ', permissionName: 'Read organization structure' }
        ]
      }
    ];
  }
}
