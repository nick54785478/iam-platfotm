import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

import { IamUsersComponent } from './components/iam-users/iam-users.component';
import { IamRolesComponent } from './components/iam-roles/iam-roles.component';
import { IamGroupsComponent } from './components/iam-groups/iam-groups.component';

@Component({
  selector: 'app-iam',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    IamUsersComponent,
    IamRolesComponent,
    IamGroupsComponent
  ],
  templateUrl: './iam.component.html',
  styleUrl: './iam.component.scss'
})
export class IamComponent {
  activeTab = signal<string>('users');
}
