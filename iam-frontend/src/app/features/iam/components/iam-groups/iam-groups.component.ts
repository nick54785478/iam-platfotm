import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, GroupRepresentation, UserRepresentation, RoleRepresentation } from '../../../../core/services/auth.service';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';

@Component({
  selector: 'app-iam-groups',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule
  ],
  templateUrl: './iam-groups.component.html',
  styleUrl: './iam-groups.component.scss'
})
export class IamGroupsComponent implements OnInit {
  private readonly authService = inject(AuthService);

  groups = signal<GroupRepresentation[]>([]);
  users = signal<UserRepresentation[]>([]);
  roles = signal<RoleRepresentation[]>([]);

  // Group form
  showAddGroupModal = signal(false);
  newGroupCode = '';
  newGroupName = '';

  // Manage Group Details
  selectedGroup = signal<GroupRepresentation | null>(null);
  userToAddGroup = '';
  roleToAddGroup = '';

  ngOnInit(): void {
    this.loadGroups();
    this.loadUsers();
    this.loadRoles();
  }

  loadGroups(): void {
    this.authService.getGroups().subscribe({
      next: (res) => this.groups.set(res?.data || []),
      error: () => this.groups.set(this.mockGroups())
    });
  }

  loadUsers(): void {
    this.authService.getUsers().subscribe({
      next: (res) => this.users.set(res?.data || [])
    });
  }

  loadRoles(): void {
    this.authService.getRoles().subscribe({
      next: (res) => this.roles.set(res?.data || [])
    });
  }

  getUsernameById(idOrName: string): string {
    const userById = this.users().find(u => u.id === idOrName);
    if (userById) return userById.username;
    const userByName = this.users().find(u => u.username === idOrName);
    if (userByName) return userByName.username;
    return idOrName;
  }

  getRoleCodeById(idOrCode: string): string {
    const roleById = this.roles().find(r => r.id === idOrCode);
    if (roleById) return roleById.roleCode;
    const roleByCode = this.roles().find(r => r.roleCode === idOrCode);
    if (roleByCode) return roleByCode.roleCode;
    return idOrCode;
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
        this.loadGroups();
        this.closeModals();
      },
      error: () => {
        this.groups.set([...this.groups(), {
          groupCode: this.newGroupCode.toUpperCase(),
          groupName: this.newGroupName,
          memberUserIds: [],
          assignedRoleIds: []
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
      const userToAdd = this.userToAddGroup;
      const userObj = this.users().find(u => u.username === userToAdd);
      const idToStore = userObj?.id || userToAdd;
      
      // Optimistic UI Update & Reset Dropdown
      const updatedGrp = { ...grp, memberUserIds: Array.from(new Set([...(grp.memberUserIds || []), idToStore])) };
      this.selectedGroup.set(updatedGrp);
      this.updateGroupInList(updatedGrp);
      this.userToAddGroup = '';

      this.authService.addMemberToGroup(grp.groupCode, userToAdd).subscribe({
        next: () => {
          // Try to fetch latest, but merge with our optimistic state in case backend is lagging
          this.authService.getGroupDetails(grp.groupCode).subscribe(res => {
            if (res?.data) {
              const freshGrp = { ...res.data, memberUserIds: Array.from(new Set([...(res.data.memberUserIds || []), ...updatedGrp.memberUserIds])) };
              this.selectedGroup.set(freshGrp);
              this.updateGroupInList(freshGrp);
            }
          });
        },
        error: () => {
          // Keep optimistic update on error for demo purposes
        }
      });
    }
  }

  onRemoveMemberFromGroup(m: string): void {
    const grp = this.selectedGroup();
    if (grp) {
      const username = this.getUsernameById(m);

      // Optimistic UI Update
      const updatedGrp = { ...grp, memberUserIds: (grp.memberUserIds || []).filter(x => x !== m) };
      this.selectedGroup.set(updatedGrp);
      this.updateGroupInList(updatedGrp);

      this.authService.removeMemberFromGroup(grp.groupCode, username).subscribe({
        next: () => {
          // Try to fetch latest, but ensure the removed member stays removed in case backend is lagging
          this.authService.getGroupDetails(grp.groupCode).subscribe(res => {
            if (res?.data) {
              const freshGrp = { ...res.data, memberUserIds: (res.data.memberUserIds || []).filter(x => x !== m) };
              this.selectedGroup.set(freshGrp);
              this.updateGroupInList(freshGrp);
            }
          });
        },
        error: () => {
          // Keep optimistic update on error
        }
      });
    }
  }

  onAssignRoleToGroup(): void {
    const grp = this.selectedGroup();
    if (grp && this.roleToAddGroup) {
      const roleToAdd = this.roleToAddGroup;
      const roleObj = this.roles().find(r => r.roleCode === roleToAdd);
      const idToStore = roleObj?.id || roleToAdd;

      // Optimistic UI Update & Reset Dropdown
      const updatedGrp = { ...grp, assignedRoleIds: Array.from(new Set([...(grp.assignedRoleIds || []), idToStore])) };
      this.selectedGroup.set(updatedGrp);
      this.updateGroupInList(updatedGrp);
      this.roleToAddGroup = '';

      this.authService.assignRoleToGroup(grp.groupCode, roleToAdd).subscribe({
        next: () => {
          // Try to fetch latest, but merge with our optimistic state in case backend is lagging
          this.authService.getGroupDetails(grp.groupCode).subscribe(res => {
            if (res?.data) {
              const freshGrp = { ...res.data, assignedRoleIds: Array.from(new Set([...(res.data.assignedRoleIds || []), ...updatedGrp.assignedRoleIds])) };
              this.selectedGroup.set(freshGrp);
              this.updateGroupInList(freshGrp);
            }
          });
        },
        error: () => {
          // Keep optimistic update on error
        }
      });
    }
  }

  onRevokeRoleFromGroup(r: string): void {
    const grp = this.selectedGroup();
    if (grp) {
      const roleCode = this.getRoleCodeById(r);

      // Optimistic UI Update
      const updatedGrp = { ...grp, assignedRoleIds: (grp.assignedRoleIds || []).filter(x => x !== r) };
      this.selectedGroup.set(updatedGrp);
      this.updateGroupInList(updatedGrp);

      this.authService.revokeRoleFromGroup(grp.groupCode, roleCode).subscribe({
        next: () => {
          // Try to fetch latest, but ensure the revoked role stays revoked in case backend is lagging
          this.authService.getGroupDetails(grp.groupCode).subscribe(res => {
            if (res?.data) {
              const freshGrp = { ...res.data, assignedRoleIds: (res.data.assignedRoleIds || []).filter(x => x !== r) };
              this.selectedGroup.set(freshGrp);
              this.updateGroupInList(freshGrp);
            }
          });
        },
        error: () => {
          // Keep optimistic update on error
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
    this.showAddGroupModal.set(false);
    this.selectedGroup.set(null);
  }

  private mockGroups(): GroupRepresentation[] {
    return [
      { groupCode: 'DEPT_TECH', groupName: 'Tech Department Group', memberUserIds: ['alex.zhang'], assignedRoleIds: ['DEVELOPER'] },
      { groupCode: 'BOARD_DIRECTORS', groupName: 'Board of Directors', memberUserIds: ['admin'], assignedRoleIds: ['ADMIN'] }
    ];
  }
}
