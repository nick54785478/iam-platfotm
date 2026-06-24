import { Component, inject } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  authService = inject(AuthService);
  private readonly router = inject(Router);

  userInitials(): string {
    const user = this.authService.currentUser();
    if (!user) return 'US';
    return user.substring(0, 2).toUpperCase();
  }

  activePageTitle(): string {
    const url = this.router.url;
    if (url.includes('org-tree')) return 'Organization Architecture';
    if (url.includes('iam')) return 'Identity & Role Matrix';
    if (url.includes('tenants')) return 'SaaS Subscription Central';
    return 'Dashboard';
  }

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
