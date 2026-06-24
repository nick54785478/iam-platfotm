import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TenantService, TenantSummary, TenantDetail } from '../../core/services/tenant.service';
import { SystemMessageService } from '../../core/services/system-message.service';
import { LoadingMaskService } from '../../core/services/loading-mask.service';

import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { InputTextareaModule } from 'primeng/inputtextarea';
import { PasswordModule } from 'primeng/password';

@Component({
  selector: 'app-tenant',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    InputTextareaModule,
    PasswordModule
  ],
  templateUrl: './tenant.component.html',
  styleUrl: './tenant.component.scss'
})
export class TenantComponent implements OnInit {
  private tenantService = inject(TenantService);
  private messageService = inject(SystemMessageService);
  private loadingService = inject(LoadingMaskService);

  tenants = signal<TenantSummary[]>([]);
  activeCount = signal(0);
  enterpriseCount = signal(0);
  suspendedCount = signal(0);

  searchQuery = '';

  // Provision form states
  provId = '';
  provName = '';
  provPlan = 'FREE';
  provEmail = '';
  provPassword = '';

  // Modal control states
  selectedDetail = signal<TenantDetail | null>(null);
  upgradeTarget = signal<TenantSummary | null>(null);
  newPlan = signal<string>('FREE');
  suspendTarget = signal<TenantSummary | null>(null);
  suspendReason = '';

  ngOnInit(): void {
    this.loadTenants();
  }

  loadTenants(): void {
    this.loadingService.show();
    this.tenantService.searchTenants(
      this.searchQuery || undefined,
      undefined,
      undefined,
      0,
      100
    ).subscribe({
      next: (res) => {
        const items = res?.data?.content || [];
        this.tenants.set(items);
        this.calculateStats(items);
        this.loadingService.hide();
      },
      error: (err) => {
        this.loadingService.hide();
        this.messageService.showError('Failed to load tenants', err.error?.message || err.message);
      }
    });
  }

  calculateStats(items: TenantSummary[]): void {
    this.activeCount.set(items.filter(t => t.status === 'ACTIVE').length);
    this.enterpriseCount.set(items.filter(t => t.planType === 'ENTERPRISE').length);
    this.suspendedCount.set(items.filter(t => t.status === 'SUSPENDED').length);
  }

  onSearch(): void {
    this.loadTenants();
  }

  getPlanClass(plan: string): string {
    return `badge-${plan.toLowerCase()}`;
  }

  viewDetails(tenant: TenantSummary): void {
    const now = new Date().toISOString();
    this.selectedDetail.set({
      tenantId: tenant.tenantId,
      companyName: tenant.companyName,
      planType: tenant.planType,
      status: tenant.status,
      adminEmail: 'admin@' + tenant.tenantId.toLowerCase() + '.com',
      suspendedReason: tenant.status === 'SUSPENDED' ? 'Subscription Payment Expired' : '',
      createdAt: now,
      updatedAt: now
    });
  }

  onProvision(): void {
    this.loadingService.show();
    this.tenantService.provisionTenant(
      this.provId,
      this.provName,
      this.provPlan,
      this.provEmail,
      this.provPassword
    ).subscribe({
      next: () => {
        this.loadingService.hide();
        this.messageService.showSuccess('Success', `Tenant ${this.provName} provisioned successfully`);
        this.loadTenants();
        this.clearProvForm();
      },
      error: (err) => {
        this.loadingService.hide();
        this.messageService.showError('Provisioning Failed', err.error?.message || err.message);
      }
    });
  }

  clearProvForm(): void {
    this.provId = '';
    this.provName = '';
    this.provPlan = 'FREE';
    this.provEmail = '';
    this.provPassword = '';
  }

  openUpgradeModal(tenant: TenantSummary): void {
    this.upgradeTarget.set(tenant);
    this.newPlan.set(tenant.planType);
  }

  onUpgradeSubmit(): void {
    const target = this.upgradeTarget();
    if (target) {
      this.loadingService.show();
      this.tenantService.upgradePlan(target.tenantId, this.newPlan()).subscribe({
        next: () => {
          this.loadingService.hide();
          this.messageService.showSuccess('Success', `Plan upgraded to ${this.newPlan()}`);
          this.loadTenants();
          this.closeModals();
        },
        error: (err) => {
          this.loadingService.hide();
          this.messageService.showError('Upgrade Failed', err.error?.message || err.message);
          this.closeModals();
        }
      });
    }
  }

  openSuspendModal(tenant: TenantSummary): void {
    this.suspendTarget.set(tenant);
    this.suspendReason = '';
  }

  onSuspendSubmit(): void {
    const target = this.suspendTarget();
    if (target) {
      this.loadingService.show();
      this.tenantService.suspendTenant(target.tenantId, this.suspendReason).subscribe({
        next: () => {
          this.loadingService.hide();
          this.messageService.showSuccess('Success', `Tenant ${target.companyName} has been suspended`);
          this.loadTenants();
          this.closeModals();
        },
        error: (err) => {
          this.loadingService.hide();
          this.messageService.showError('Suspension Failed', err.error?.message || err.message);
          this.closeModals();
        }
      });
    }
  }

  closeModals(): void {
    this.selectedDetail.set(null);
    this.upgradeTarget.set(null);
    this.suspendTarget.set(null);
  }
}
