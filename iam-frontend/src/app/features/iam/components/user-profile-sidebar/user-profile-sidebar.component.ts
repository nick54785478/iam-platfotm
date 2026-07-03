import { Component, Input, Output, EventEmitter, inject, signal, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService, UserRepresentation, UserPermissionContextRepresentation } from '../../../../core/services/auth.service';
import { DeptService } from '../../../../core/services/dept.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { SidebarModule } from 'primeng/sidebar';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-user-profile-sidebar',
  standalone: true,
  imports: [CommonModule, SidebarModule, ButtonModule],
  templateUrl: './user-profile-sidebar.component.html',
  styleUrl: './user-profile-sidebar.component.scss'
})
export class UserProfileSidebarComponent implements OnChanges {
  private authService = inject(AuthService);
  private deptService = inject(DeptService);

  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Input() user: UserRepresentation | null = null;
  
  profileContext = signal<UserPermissionContextRepresentation | null>(null);
  profileLoading = signal(false);
  activeProfileTab = signal<'DEPT' | 'ROLES' | 'PERMISSIONS'>('DEPT');

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue === true && this.user) {
      this.loadProfile(this.user);
    }
  }

  loadProfile(user: UserRepresentation): void {
    this.profileLoading.set(true);
    this.activeProfileTab.set('DEPT');

    const tenantId = this.authService.currentTenant() || 'TENANT_DEFAULT';
    
    forkJoin({
      authCtx: this.authService.getUserPermissionsContext(user.username).pipe(catchError(() => of(null))),
      deptCtx: this.deptService.getUserDepartmentTree(tenantId, user.username).pipe(catchError(() => of(null)))
    }).subscribe({
      next: ({ authCtx, deptCtx }) => {
        const contextData = authCtx?.data || null;

        if (contextData) {
          const deptTree = deptCtx?.data || [];
          contextData.departments = deptTree.map((node: any) => node.name);
          this.profileContext.set(contextData);
        } else {
          this.profileContext.set({
            username: user.username,
            email: user.email,
            status: user.status,
            personalRoles: user.roles || [],
            groupRoles: [],
            permissions: [],
            departments: []
          });
        }
        this.profileLoading.set(false);
      }
    });
  }

  onVisibleChange(val: boolean): void {
    if (!val) {
      this.visibleChange.emit(false);
      this.profileContext.set(null);
    }
  }
}
