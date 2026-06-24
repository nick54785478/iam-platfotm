import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService, UserRepresentation, RoleRepresentation, GroupRepresentation } from '../../core/services/auth.service';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';

@Component({
  selector: 'app-iam',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    PasswordModule
  ],
  templateUrl: './iam.component.html',
  styleUrl: './iam.component.scss'
})
export class IamComponent implements OnInit {
  private readonly authService = inject(AuthService);

  activeTab = signal<string>('users');

  users = signal<UserRepresentation[]>([]);
  roles = signal<RoleRepresentation[]>([]);
  groups = signal<GroupRepresentation[]>([]);

  // User form
  showAddUserModal = signal(false);
  newUsername = '';
  newEmail = '';
  newPassword = '';

  // Assign Role User Modal
  roleUserTarget = signal<UserRepresentation | null>(null);
  selectedRoleCode = '';

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

  // Group form
  showAddGroupModal = signal(false);
  newGroupCode = '';
  newGroupName = '';

  // Manage Group Details
  selectedGroup = signal<GroupRepresentation | null>(null);
  userToAddGroup = '';
  roleToAddGroup = '';

  ngOnInit(): void {
    this.loadAll();
  }

  loadAll(): void {
    this.authService.getUsers().subscribe({
      next: (res) => this.users.set(res?.data || []),
      error: () => this.users.set(this.mockUsers())
    });

    this.authService.getRoles().subscribe({
      next: (res) => this.roles.set(res?.data || []),
      error: () => this.roles.set(this.mockRoles())
    });

    this.authService.getGroups().subscribe({
      next: (res) => this.groups.set(res?.data || []),
      error: () => this.groups.set(this.mockGroups())
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
        this.loadAll();
        this.closeModals();
      },
      error: () => {
        // Fallback mock append
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
      next: () => this.loadAll(),
      error: () => {
        // Mute state
        this.users.set(this.users().map(u =>
          u.username === username ? { ...u, status: 'DEACTIVATED' } : u
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
          this.loadAll();
          this.closeModals();
        },
        error: () => {
          // Local fallback state
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

  // --- Roles Handlers ---
  openAddRoleModal(): void {
    this.showAddRoleModal.set(true);
    this.newRoleCode = '';
    this.newRoleName = '';
  }

  onCreateRole(): void {
    this.authService.createRole(this.newRoleName, this.newRoleCode).subscribe({
      next: () => {
        this.loadAll();
        this.closeModals();
      },
      error: () => {
        // Mock fallback
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
          this.loadAll();
          this.closeModals();
        },
        error: () => {
          // Local fallback
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
          this.loadAll();
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

  // --- Groups Handlers ---
  openAddGroupModal(): void {
    this.showAddGroupModal.set(true);
    this.newGroupCode = '';
    this.newGroupName = '';
  }

  onCreateGroup(): void {
    this.authService.createGroup(this.newGroupName, this.newGroupCode).subscribe({
      next: () => {
        this.loadAll();
        this.closeModals();
      },
      error: () => {
        this.groups.set([...this.groups(), {
          groupCode: this.newGroupCode.toUpperCase(),
          groupName: this.newGroupName,
          members: [],
          roles: []
        }]);
        this.closeModals();
      }
    });
  }

  openGroupDetail(group: GroupRepresentation): void {
    this.authService.getGroupDetails(group.groupCode).subscribe({
      next: (res) => this.selectedGroup.set(res?.data),
      error: () => this.selectedGroup.set(group)
    });
    this.userToAddGroup = '';
    this.roleToAddGroup = '';
  }

  onAddMemberToGroup(): void {
    const grp = this.selectedGroup();
    if (grp && this.userToAddGroup) {
      this.authService.addMemberToGroup(grp.groupCode, this.userToAddGroup).subscribe({
        next: () => {
          this.refreshSelectedGroup(grp.groupCode);
        },
        error: () => {
          // Local update
          const updatedGrp = { ...grp, members: [...(grp.members || []), this.userToAddGroup] };
          this.selectedGroup.set(updatedGrp);
          this.updateGroupInList(updatedGrp);
        }
      });
    }
  }

  onRemoveMemberFromGroup(username: string): void {
    const grp = this.selectedGroup();
    if (grp) {
      this.authService.removeMemberFromGroup(grp.groupCode, username).subscribe({
        next: () => {
          this.refreshSelectedGroup(grp.groupCode);
        },
        error: () => {
          const updatedGrp = { ...grp, members: (grp.members || []).filter(m => m !== username) };
          this.selectedGroup.set(updatedGrp);
          this.updateGroupInList(updatedGrp);
        }
      });
    }
  }

  onAssignRoleToGroup(): void {
    const grp = this.selectedGroup();
    if (grp && this.roleToAddGroup) {
      this.authService.assignRoleToGroup(grp.groupCode, this.roleToAddGroup).subscribe({
        next: () => {
          this.refreshSelectedGroup(grp.groupCode);
        },
        error: () => {
          const updatedGrp = { ...grp, roles: [...(grp.roles || []), this.roleToAddGroup] };
          this.selectedGroup.set(updatedGrp);
          this.updateGroupInList(updatedGrp);
        }
      });
    }
  }

  onRevokeRoleFromGroup(roleCode: string): void {
    const grp = this.selectedGroup();
    if (grp) {
      this.authService.revokeRoleFromGroup(grp.groupCode, roleCode).subscribe({
        next: () => {
          this.refreshSelectedGroup(grp.groupCode);
        },
        error: () => {
          const updatedGrp = { ...grp, roles: (grp.roles || []).filter(r => r !== roleCode) };
          this.selectedGroup.set(updatedGrp);
          this.updateGroupInList(updatedGrp);
        }
      });
    }
  }

  private refreshSelectedGroup(groupCode: string): void {
    this.authService.getGroupDetails(groupCode).subscribe(res => {
      if (res?.data) {
        this.selectedGroup.set(res.data);
        this.updateGroupInList(res.data);
      }
    });
  }

  private updateGroupInList(updated: GroupRepresentation): void {
    this.groups.set(this.groups().map(g => g.groupCode === updated.groupCode ? updated : g));
  }

  closeModals(): void {
    this.showAddUserModal.set(false);
    this.roleUserTarget.set(null);
    this.showAddRoleModal.set(false);
    this.permRoleTarget.set(null);
    this.renameRoleTarget.set(null);
    this.showAddGroupModal.set(false);
    this.selectedGroup.set(null);
  }

  // --- Mock fallbacks ---
  private mockUsers(): UserRepresentation[] {
    return [
      { username: 'admin', email: 'admin@company.com', status: 'ACTIVE', roles: ['ADMIN'] },
      { username: 'alex.zhang', email: 'alex.zhang@company.com', status: 'ACTIVE', roles: ['DEVELOPER'] },
      { username: 'tony.wang', email: 'tony.wang@company.com', status: 'DEACTIVATED', roles: ['GUEST'] }
    ];
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

  private mockGroups(): GroupRepresentation[] {
    return [
      { groupCode: 'DEPT_TECH', groupName: 'Tech Department Group', members: ['alex.zhang'], roles: ['DEVELOPER'] },
      { groupCode: 'BOARD_DIRECTORS', groupName: 'Board of Directors', members: ['admin'], roles: ['ADMIN'] }
    ];
  }
}
