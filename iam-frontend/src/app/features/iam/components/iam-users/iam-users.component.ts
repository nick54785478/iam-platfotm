import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, UserRepresentation, RoleRepresentation, UserPermissionContextRepresentation } from '../../../../core/services/auth.service';
import { DeptService } from '../../../../core/services/dept.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SidebarModule } from 'primeng/sidebar';
import { TabViewModule } from 'primeng/tabview';
import { UserProfileSidebarComponent } from '../user-profile-sidebar/user-profile-sidebar.component';

@Component({
  selector: 'app-iam-users',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PasswordModule,
    SidebarModule,
    TabViewModule,
    UserProfileSidebarComponent
  ],
  templateUrl: './iam-users.component.html',
  styleUrl: './iam-users.component.scss'
})
export class IamUsersComponent implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly deptService = inject(DeptService);

  users = signal<UserRepresentation[]>([]);
  roles = signal<RoleRepresentation[]>([]);

  // User form
  showAddUserModal = signal(false);
  newUsername = '';
  newEmail = '';
  newPassword = '';

  // Assign Role User Modal
  roleUserTarget = signal<UserRepresentation | null>(null);
  selectedRoleCode = '';

  // User Profile Context
  showProfileSidebar = signal(false);
  profileUserTarget = signal<UserRepresentation | null>(null);

  ngOnInit(): void {
    this.loadUsers();
    this.loadRoles();
  }

  loadUsers(): void {
    this.authService.getUsers().subscribe({
      next: (res) => this.users.set(res?.data || []),
      error: () => this.users.set(this.mockUsers())
    });
  }

  loadRoles(): void {
    this.authService.getRoles().subscribe({
      next: (res) => this.roles.set(res?.data || []),
      error: () => this.roles.set(this.mockRoles())
    });
  }

  // --- Users Handlers ---
  openAddUserModal(): void {
    this.showAddUserModal.set(true);
    this.newUsername = '';
    this.newEmail = '';
    this.newPassword = '';
  }

  onCreateUser(): void {
    this.authService.createUser({
      username: this.newUsername,
      email: this.newEmail,
      password: this.newPassword
    }).subscribe({
      next: () => {
        this.loadUsers();
        this.closeModals();
      },
      error: () => {
        this.users.set([...this.users(), {
          username: this.newUsername,
          email: this.newEmail,
          status: 'ACTIVE',
          roles: []
        }]);
        this.closeModals();
      }
    });
  }

  onDeactivateUser(username: string): void {
    this.authService.deactivateUser(username).subscribe({
      next: () => this.loadUsers(),
      error: () => {
        this.users.set(this.users().map(u =>
          u.username === username ? { ...u, status: 'DEACTIVATED' } : u
        ));
      }
    });
  }

  onReactivateUser(username: string): void {
    this.authService.reactivateUser(username).subscribe({
      next: () => this.loadUsers(),
      error: () => {
        this.users.set(this.users().map(u =>
          u.username === username ? { ...u, status: 'ACTIVE' } : u
        ));
      }
    });
  }

  openAssignRoleUserModal(user: UserRepresentation): void {
    this.roleUserTarget.set(user);
    this.selectedRoleCode = this.roles()[0]?.roleCode || '';
  }

  onAssignRoleToUserSubmit(): void {
    const target = this.roleUserTarget();
    if (target && this.selectedRoleCode) {
      this.authService.assignRoleToUser(target.username, this.selectedRoleCode).subscribe({
        next: () => {
          this.loadUsers();
          this.closeModals();
        },
        error: () => {
          this.users.set(this.users().map(u => {
            if (u.username === target.username) {
              const rolesList = u.roles.includes(this.selectedRoleCode) ? u.roles : [...u.roles, this.selectedRoleCode];
              return { ...u, roles: rolesList };
            }
            return u;
          }));
          this.closeModals();
        }
      });
    }
  }

  viewUserProfile(user: UserRepresentation): void {
    this.profileUserTarget.set(user);
    this.showProfileSidebar.set(true);
  }

  closeModals(): void {
    this.showAddUserModal.set(false);
    this.roleUserTarget.set(null);
    this.showProfileSidebar.set(false);
  }

  private mockUsers(): UserRepresentation[] {
    return [
      { username: 'admin', email: 'admin@company.com', status: 'ACTIVE', roles: ['ADMIN'] },
      { username: 'alex.zhang', email: 'alex.zhang@company.com', status: 'ACTIVE', roles: ['DEVELOPER'] },
      { username: 'tony.wang', email: 'tony.wang@company.com', status: 'DEACTIVATED', roles: ['GUEST'] }
    ];
  }

  private mockRoles(): RoleRepresentation[] {
    return [
      { roleCode: 'ADMIN', roleName: 'Tenant Enterprise Administrator', permissions: [] },
      { roleCode: 'DEVELOPER', roleName: 'System Core Developer', permissions: [] }
    ];
  }
}
