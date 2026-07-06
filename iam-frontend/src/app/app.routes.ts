import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login.component';
import { RegisterComponent } from './features/auth/register.component';
import { DashboardComponent } from './features/dashboard.component';
import { DeptComponent } from './features/dept/dept.component';
import { IamComponent } from './features/iam/iam.component';
import { TenantComponent } from './features/tenant/tenant.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  {
    path: 'dashboard',
    component: DashboardComponent,
    children: [
      { path: 'org-tree', component: DeptComponent },
      { path: 'iam', component: IamComponent },
      { path: 'tenants', component: TenantComponent },
      { path: 'dept-permissions', loadComponent: () => import('./features/dept/components/dept-permissions/dept-permissions.component').then(m => m.DeptPermissionsComponent) },
      { path: 'kyc', loadComponent: () => import('./features/kyc/kyc.component').then(m => m.KycComponent) },
      { path: '', redirectTo: 'org-tree', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' }
];
