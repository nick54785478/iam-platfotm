import { Component, inject, signal } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { TenantService } from '../../core/services/tenant.service';

import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule, 
    RouterModule,
    InputTextModule,
    PasswordModule,
    ButtonModule
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {
  private tenantService = inject(TenantService);
  private router = inject(Router);

  tenantId = '';
  companyName = '';
  planType = 'FREE';
  adminEmail = '';
  plainPassword = '';

  isLoading = signal(false);
  successMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);

  onSubmit(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    this.tenantService.provisionTenant(
      this.tenantId, 
      this.companyName, 
      this.planType, 
      this.adminEmail, 
      this.plainPassword
    ).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.successMessage.set('Tenant space registered successfully! Redirecting to login...');
        setTimeout(() => {
          this.router.navigate(['/login']);
        }, 2000);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err?.error?.message || 'Registration failed. Please check your data.');
      }
    });
  }
}
