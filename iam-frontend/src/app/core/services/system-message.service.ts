import { Injectable } from '@angular/core';
import { MessageService, ConfirmationService } from 'primeng/api';

/**
 * 系統訊息與通知服務 (System Message Service)
 * 封裝 PrimeNG 的 MessageService 與 ConfirmationService，
 * 提供統一的 Toast 通知與危險動作確認對話框功能。
 */
@Injectable({
  providedIn: 'root',
})
export class SystemMessageService {
  constructor(
    private messageService: MessageService,
    private confirmationService: ConfirmationService,
  ) {}

  /**
   * 顯示成功通知 (Success Toast)
   * @param summary 通知標題
   * @param detail 通知詳細內容
   */
  showSuccess(summary: string, detail: string): void {
    this.messageService.add({
      key: 'msg',
      severity: 'success',
      summary,
      detail,
      icon: 'pi-check',
    });
  }

  /**
   * 顯示警告通知 (Warning Toast)
   * @param summary 通知標題
   * @param detail 通知詳細內容
   */
  showWarn(summary: string, detail: string): void {
    this.messageService.add({
      key: 'msg',
      severity: 'warn',
      summary,
      detail,
      icon: 'pi-exclamation-triangle', // 修正為較合適的警告 icon (原為 pi-check)
    });
  }

  /**
   * 顯示錯誤通知 (Error Toast)
   * @param summary 通知標題
   * @param detail 通知詳細內容
   */
  showError(summary: string, detail: string): void {
    this.messageService.add({
      key: 'msg',
      severity: 'error',
      summary,
      detail,
      icon: 'pi-times-circle',
    });
  }

  /**
   * 執行危險動作前的二次確認對話框
   * @param header 對話框標題 (例如：'刪除確認')
   * @param message 警告或確認訊息內容
   * @param onAccept 按下「確定」後執行的回呼函式
   */
  confirmAction(header: string, message: string, onAccept: () => void): void {
    this.confirmationService.confirm({
      header,
      message,
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: '確定',
      rejectLabel: '取消',
      // UX 優化：將確定按鈕改為紅色警告色，並將取消按鈕設為次要樣式
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'p-button-secondary p-button-outlined',
      defaultFocus: 'reject', // 防呆機制：預設焦點在「取消」上，避免誤按 Enter 觸發危險動作
      accept: onAccept,
    });
  }
}
