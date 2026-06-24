import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { DeptService, DepartmentTreeNode, DepartmentFlatNode, DepartmentTemporalState } from '../../core/services/dept.service';
import { AuthService } from '../../core/services/auth.service';

import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';

@Component({
  selector: 'app-dept',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    DialogModule,
    InputTextModule
  ],
  templateUrl: './dept.component.html',
  styleUrl: './dept.component.scss'
})
export class DeptComponent implements OnInit {
  private readonly deptService = inject(DeptService);
  private readonly authService = inject(AuthService);

  searchKeyword = '';

  // Org Tree hierarchies
  rootNodes = signal<DepartmentTreeNode[]>([]);
  flatDeptsList = signal<DepartmentFlatNode[]>([]);

  // Selected department details
  selectedNode = signal<DepartmentTreeNode | null>(null);
  inspectorTab = signal<string>('personnel');

  // Personnel assignment Form states
  activeEmployees = signal<string[]>([]);
  employeeToAssign = '';

  // Restructure form states
  nodeSortOrder = 0;
  mergeTargetId = '';

  // Time Machine States
  eventTimeline = signal<any[]>([]);
  replayTimestamp = signal<string>(new Date().toISOString());
  sliderIndex = signal<number>(0);
  temporalState = signal<DepartmentTemporalState | null>(null);

  // Modal controls
  showCreateModal = signal(false);
  createTargetParent = signal<DepartmentTreeNode | null>(null);
  newDeptId = '';
  newDeptCode = '';
  newDeptName = '';

  renameTarget = signal<DepartmentTreeNode | null>(null);
  newRenameVal = '';

  ngOnInit(): void {
    this.loadTree();
  }

  loadTree(): void {
    const tenantId = this.authService.currentTenant() || 'DEFAULT';
    // Retrieve root tree layout. Usually started at a pre-provisioned 'ROOT' department
    this.deptService.getTree(tenantId, 'ROOT', true).subscribe({
      next: (res) => {
        this.rootNodes.set([res]);
        this.flattenTree([res]);
      },
      error: () => {
        // Fallback mockup tree if database is empty or offline
        const mockRoot = this.mockTree();
        this.rootNodes.set([mockRoot]);
        this.flattenTree([mockRoot]);
      }
    });
  }

  flattenTree(nodes: DepartmentTreeNode[]): void {
    const temp: DepartmentFlatNode[] = [];
    const traverse = (n: DepartmentTreeNode) => {
      temp.push({
        id: n.id,
        parentId: n.parentId,
        code: n.code,
        name: n.name,
        sortOrder: n.sortOrder,
        status: n.status
      });
      if (n.children) {
        n.children.forEach(traverse);
      }
    };
    nodes.forEach(traverse);
    this.flatDeptsList.set(temp);
  }

  selectNode(node: DepartmentTreeNode): void {
    this.selectedNode.set(node);
    this.nodeSortOrder = node.sortOrder;
    this.mergeTargetId = '';
    this.loadNodePersonnel(node.id);
    this.loadTimeMachineData(node.id);
  }

  loadNodePersonnel(deptId: string): void {
    this.deptService.getHierarchy(deptId).subscribe({
      next: (res) => {
        this.activeEmployees.set(res?.data?.employees || []);
      },
      error: () => {
        // Mock fallback personnel list
        if (deptId === 'ROOT') this.activeEmployees.set(['admin']);
        else if (deptId === 'DEPT_TECH') this.activeEmployees.set(['alex.zhang']);
        else this.activeEmployees.set([]);
      }
    });
  }

  loadTimeMachineData(deptId: string): void {
    const tenantId = this.authService.currentTenant() || 'DEFAULT';
    this.deptService.getEventHistory(tenantId, deptId).subscribe({
      next: (res) => {
        this.eventTimeline.set(res || []);
        this.sliderIndex.set(res.length - 1);
        if (res.length > 0) {
          const latest = res[res.length - 1];
          this.replayTimestamp.set(latest.occurredAt);
          this.fetchTemporalStateAt(latest.occurredAt);
        }
      },
      error: () => {
        // Mock events timeline fallback
        const mockEvents = this.mockEvents(deptId);
        this.eventTimeline.set(mockEvents);
        this.sliderIndex.set(mockEvents.length - 1);
        if (mockEvents.length > 0) {
          const latest = mockEvents[mockEvents.length - 1];
          this.replayTimestamp.set(latest.occurredAt);
          this.temporalState.set(this.mockTemporalState(deptId, latest.eventType, latest.version));
        }
      }
    });
  }

  fetchTemporalStateAt(timestamp: string): void {
    const tenantId = this.authService.currentTenant() || 'DEFAULT';
    const dept = this.selectedNode();
    if (dept) {
      this.deptService.getStateAt(tenantId, dept.id, timestamp).subscribe({
        next: (res) => this.temporalState.set(res),
        error: () => {
          // If mock, simulate based on index
          const ev = this.eventTimeline()[this.sliderIndex()];
          if (ev) {
            this.temporalState.set(this.mockTemporalState(dept.id, ev.eventType, ev.version));
          }
        }
      });
    }
  }

  onSliderChange(event: any): void {
    const index = parseInt(event.target.value);
    this.sliderIndex.set(index);
    const ev = this.eventTimeline()[index];
    if (ev) {
      this.replayTimestamp.set(ev.occurredAt);
      this.fetchTemporalStateAt(ev.occurredAt);
    }
  }

  onSearch(): void {
    if (!this.searchKeyword) {
      this.loadTree();
      return;
    }
    const tenantId = this.authService.currentTenant() || 'DEFAULT';
    this.deptService.searchDepartments(tenantId, this.searchKeyword).subscribe({
      next: (res) => {
        // Search API returns a flat list. We map it to nodes
        const nodes: DepartmentTreeNode[] = res.map((item: any) => ({
          ...item,
          directEmployeeCount: 0,
          totalEmployeeCount: 0
        }));
        this.rootNodes.set(nodes);
      },
      error: () => {
        const filtered = this.flatDeptsList().filter(d =>
          d.name.toLowerCase().includes(this.searchKeyword.toLowerCase()) ||
          d.code.toLowerCase().includes(this.searchKeyword.toLowerCase())
        );
        const nodes: DepartmentTreeNode[] = filtered.map(item => ({
          ...item,
          directEmployeeCount: 0,
          totalEmployeeCount: 0
        }));
        this.rootNodes.set(nodes);
      }
    });
  }

  // --- Personnel Actions ---
  onAssignEmployee(): void {
    const dept = this.selectedNode();
    if (dept && this.employeeToAssign) {
      this.deptService.assignEmployee(dept.id, this.employeeToAssign).subscribe({
        next: () => {
          this.loadNodePersonnel(dept.id);
          this.loadTree();
          this.employeeToAssign = '';
        },
        error: () => {
          this.activeEmployees.set([...this.activeEmployees(), this.employeeToAssign]);
          this.employeeToAssign = '';
        }
      });
    }
  }

  onUnassignEmployee(empId: string): void {
    const dept = this.selectedNode();
    if (dept) {
      this.deptService.unassignEmployee(dept.id, empId).subscribe({
        next: () => {
          this.loadNodePersonnel(dept.id);
          this.loadTree();
        },
        error: () => {
          this.activeEmployees.set(this.activeEmployees().filter(e => e !== empId));
        }
      });
    }
  }

  // --- Restructure Commands ---
  onSaveSortOrder(): void {
    const dept = this.selectedNode();
    if (dept) {
      this.deptService.changeSortOrder(dept.id, this.nodeSortOrder).subscribe({
        next: () => this.loadTree(),
        error: () => {
          dept.sortOrder = this.nodeSortOrder;
          this.loadTree();
        }
      });
    }
  }

  onMergeDepartments(): void {
    const source = this.selectedNode();
    if (source && this.mergeTargetId) {
      if (confirm(`Are you absolutely sure you want to merge ${source.name} into the target department? This will disable ${source.name} permanently.`)) {
        this.deptService.mergeDepartment(source.id, this.mergeTargetId).subscribe({
          next: () => {
            this.selectedNode.set(null);
            this.loadTree();
          },
          error: () => {
            // Mock restructure
            const targetNode = this.flatDeptsList().find(d => d.id === this.mergeTargetId);
            alert(`Merger mock execute: ${source.name} assets merged into ${targetNode?.name}`);
            this.selectedNode.set(null);
            this.loadTree();
          }
        });
      }
    }
  }

  // --- Life Cycle commands ---
  openCreateDeptModal(parent: DepartmentTreeNode | null): void {
    this.createTargetParent.set(parent);
    this.newDeptId = '';
    this.newDeptCode = '';
    this.newDeptName = '';
    this.showCreateModal.set(true);
  }

  onCreateDept(): void {
    const parent = this.createTargetParent();
    this.deptService.createDepartment(
      this.newDeptId.toUpperCase(),
      parent ? parent.id : null,
      this.newDeptCode.toUpperCase(),
      this.newDeptName
    ).subscribe({
      next: () => {
        this.loadTree();
        this.closeModals();
      },
      error: () => {
        // Local tree simulate insert
        const parentId = parent ? parent.id : null;
        const newNode: DepartmentTreeNode = {
          id: this.newDeptId.toUpperCase(),
          parentId: parentId,
          code: this.newDeptCode.toUpperCase(),
          name: this.newDeptName,
          sortOrder: 0,
          status: 'ACTIVE',
          directEmployeeCount: 0,
          totalEmployeeCount: 0,
          children: []
        };

        if (parent) {
          parent.children = [...(parent.children || []), newNode];
        } else {
          this.rootNodes.set([...this.rootNodes(), newNode]);
        }
        this.flattenTree(this.rootNodes());
        this.closeModals();
      }
    });
  }

  openRenameModal(node: DepartmentTreeNode): void {
    this.renameTarget.set(node);
    this.newRenameVal = node.name;
  }

  onRenameDeptSubmit(): void {
    const target = this.renameTarget();
    if (target && this.newRenameVal) {
      this.deptService.renameDepartment(target.id, this.newRenameVal).subscribe({
        next: () => {
          this.loadTree();
          this.closeModals();
        },
        error: () => {
          target.name = this.newRenameVal;
          this.closeModals();
        }
      });
    }
  }

  onDisableDept(id: string): void {
    if (confirm('Deactivating a department disables scheduling and locks user profile assignments. Proceed?')) {
      this.deptService.disableDepartment(id).subscribe({
        next: () => this.loadTree(),
        error: () => {
          // Find and change locally
          const findAndDisable = (n: DepartmentTreeNode) => {
            if (n.id === id) n.status = 'DISABLED';
            if (n.children) n.children.forEach(findAndDisable);
          };
          this.rootNodes().forEach(findAndDisable);
        }
      });
    }
  }

  onDeleteDept(id: string): void {
    if (confirm('Cascading delete will remove all sub-departments. This operation records Outbox ES logs. Continue?')) {
      this.deptService.deleteDepartment(id).subscribe({
        next: () => {
          this.selectedNode.set(null);
          this.loadTree();
        },
        error: () => {
          // Remove local node
          const filterNode = (list: DepartmentTreeNode[]): DepartmentTreeNode[] => {
            return list.filter(n => {
              if (n.id === id) return false;
              if (n.children) n.children = filterNode(n.children);
              return true;
            });
          };
          this.rootNodes.set(filterNode(this.rootNodes()));
          this.flattenTree(this.rootNodes());
          this.selectedNode.set(null);
        }
      });
    }
  }

  onRestoreDept(): void {
    const dept = this.selectedNode();
    if (dept) {
      this.deptService.restoreDepartment(dept.id).subscribe({
        next: () => this.loadTree(),
        error: () => {
          dept.status = 'ACTIVE';
        }
      });
    }
  }

  // --- Drag and Drop Handlers for hierarchy movements ---
  onDragStart(event: DragEvent, node: DepartmentTreeNode): void {
    event.dataTransfer?.setData('text/plain', node.id);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
  }

  onDrop(event: DragEvent, targetNode: DepartmentTreeNode): void {
    event.preventDefault();
    const sourceId = event.dataTransfer?.getData('text/plain');
    if (sourceId && sourceId !== targetNode.id) {
      if (confirm(`Do you want to re-parent department ${sourceId} to be under ${targetNode.name}?`)) {
        this.deptService.moveDepartment(sourceId, targetNode.id).subscribe({
          next: () => this.loadTree(),
          error: () => {
            alert(`Moved ${sourceId} under ${targetNode.name} (Simulation)`);
            this.loadTree();
          }
        });
      }
    }
  }

  closeModals(): void {
    this.showCreateModal.set(false);
    this.createTargetParent.set(null);
    this.renameTarget.set(null);
  }

  // --- Mock Data Generators ---
  private mockTree(): DepartmentTreeNode {
    return {
      id: 'ROOT',
      parentId: null,
      code: 'ROOT',
      name: 'Global HQ Operations',
      sortOrder: 0,
      status: 'ACTIVE',
      directEmployeeCount: 1,
      totalEmployeeCount: 2,
      children: [
        {
          id: 'DEPT_TECH',
          parentId: 'ROOT',
          code: 'TECH',
          name: 'Core Technology Center',
          sortOrder: 1,
          status: 'ACTIVE',
          directEmployeeCount: 1,
          totalEmployeeCount: 1,
          children: []
        },
        {
          id: 'DEPT_HR',
          parentId: 'ROOT',
          code: 'HR',
          name: 'Human Resources Space',
          sortOrder: 2,
          status: 'ACTIVE',
          directEmployeeCount: 0,
          totalEmployeeCount: 0,
          children: []
        }
      ]
    };
  }

  private mockEvents(deptId: string): any[] {
    return [
      { eventId: '1', eventType: 'DepartmentCreatedEvent', operatorId: 'admin', version: 1, occurredAt: new Date(Date.now() - 3600000 * 5).toISOString() },
      { eventId: '2', eventType: 'DepartmentRenamedEvent', operatorId: 'admin', version: 2, occurredAt: new Date(Date.now() - 3600000 * 2).toISOString() },
      { eventId: '3', eventType: 'EmployeeAssignedEvent', operatorId: 'admin', version: 3, occurredAt: new Date().toISOString() }
    ];
  }

  private mockTemporalState(deptId: string, eventType: string, version: number): DepartmentTemporalState {
    return {
      id: deptId,
      parentId: 'ROOT',
      code: deptId.replace('DEPT_', ''),
      name: deptId === 'DEPT_TECH' ? 'Core Technology Center' : 'Global HQ Operations',
      status: 'ACTIVE',
      activeEmployeeCount: version >= 3 ? 1 : 0,
      version: version,
      lastModifiedAt: new Date().toISOString()
    };
  }
}
