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
    this.showAddGroupModal.set(false);
    this.selectedGroup.set(null);
  }

  private mockGroups(): GroupRepresentation[] {
    return [
      { groupCode: 'DEPT_TECH', groupName: 'Tech Department Group', members: ['alex.zhang'], roles: ['DEVELOPER'] },
      { groupCode: 'BOARD_DIRECTORS', groupName: 'Board of Directors', members: ['admin'], roles: ['ADMIN'] }
    ];
  }
}
