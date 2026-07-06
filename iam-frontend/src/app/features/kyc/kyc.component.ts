import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

@Component({
  selector: 'app-kyc',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule],
  templateUrl: './kyc.component.html',
  styleUrl: './kyc.component.scss'
})
export class KycComponent implements OnInit {
  kycForm: FormGroup;
  statusMessage = '';
  statusType: 'success' | 'error' | '' = '';
  isLoading = false;
  isSubmitted = false;
  fullAddressDisplay = '';
  kycStatus = '';

  mockUserId = 'U-9527-DEV';

  private fb = inject(FormBuilder);
  private http = inject(HttpClient);

  constructor() {
    this.kycForm = this.fb.group({
      firstName: ['', Validators.required],
      lastName: ['', Validators.required],
      dateOfBirth: ['', Validators.required],
      countryCode: ['', Validators.required],
      documentType: ['', Validators.required],
      idNumber: ['', Validators.required],
      addrCountry: ['', Validators.required],
      addrState: ['', Validators.required],
      addrCity: ['', Validators.required],
      addrPostalCode: ['', Validators.required],
      addrDetail: ['', Validators.required]
    });
  }

  ngOnInit() {
    this.loadData(true);
  }

  loadData(isInitialLoad: boolean = false) {
    if (!this.mockUserId) {
      if (!isInitialLoad) this.showMessage('請先輸入 User ID', 'error');
      return;
    }

    this.isLoading = true;
    this.http.get<any>('/api/kyc/me', {
      headers: { 'X-User-Id': this.mockUserId }
    }).subscribe({
      next: (res) => {
        if (res.data && res.data.nationalIdNumber) {
          const d = res.data;
          this.kycForm.patchValue({
            firstName: d.firstName,
            lastName: d.lastName,
            dateOfBirth: d.dateOfBirth,
            documentType: d.documentType,
            idNumber: d.nationalIdNumber,
            countryCode: d.nationalIdCountry
          });
          this.fullAddressDisplay = d.fullAddress;
          this.kycStatus = d.status;

          this.kycForm.disable();
          this.isSubmitted = true;
          if (!isInitialLoad) this.showMessage('已成功載入您的 KYC 資料', 'success');
        } else {
          this.kycForm.enable();
          this.isSubmitted = false;
          this.fullAddressDisplay = '';
          this.kycStatus = '';
          if (!isInitialLoad) this.showMessage('找不到您的 KYC 資料', 'error');
        }
      },
      error: (err) => {
        console.error(err);
        if (!isInitialLoad) this.showMessage('載入失敗或無資料', 'error');
        this.isLoading = false;
      },
      complete: () => {
        this.isLoading = false;
      }
    });
  }

  onSubmit() {
    if (this.kycForm.invalid) {
      this.kycForm.markAllAsTouched();
      this.showMessage('請填寫所有必填欄位', 'error');
      return;
    }

    this.isLoading = true;
    this.http.post<any>('/api/kyc/submit', this.kycForm.value, {
      headers: { 'X-User-Id': this.mockUserId }
    }).subscribe({
      next: (res) => {
        this.showMessage('提交成功！您的資料已進入審核流程。', 'success');
        this.kycForm.disable();
        this.isSubmitted = true;
      },
      error: (err) => {
        console.error(err);
        this.showMessage('提交失敗，請檢查資料', 'error');
        this.isLoading = false;
      },
      complete: () => {
        this.isLoading = false;
      }
    });
  }

  private showMessage(text: string, type: 'success' | 'error') {
    this.statusMessage = text;
    this.statusType = type;
  }
}
