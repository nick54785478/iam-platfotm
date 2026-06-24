import { Injectable } from '@angular/core';
import { ConfirmationService } from 'primeng/api';

/**
 * 通用對話框確認服務 (Dialog Confirm Service)
 * 提供各種情境下的確認對話框封裝，例如刪除確認、未儲存變更確認等。
 */
@Injectable({
  providedIn: 'root',
})
export class DialogConfirmService {
  constructor(private confirmationService: ConfirmationService) {}

  /**
   * 顯示確認是否刪除的對話框。
   *
   * @param acceptCallback 按下「確認刪除」後執行的 callback 函式
   * @param hint (可選) 要刪除的資料關鍵字或名稱，用於顯示在提示訊息中
   * @param rejectCallback (可選) 按下「取消」後執行的 callback 函式
   */
  confirmDelete(
    acceptCallback: () => void,
    hint?: string,
    rejectCallback?: () => void,
  ): void {
    // PrimeNG 內建的樣式類別設定
    const dangerBtnClass = 'p-button-danger'; // 讓確認按鈕變成紅色 (強調破壞性行為)
    const textBtnClass = 'p-button-text p-button-secondary'; // 讓取消按鈕變成低調的純文字按鈕
    const warningIcon = 'pi pi-exclamation-triangle text-red-500'; // 警告三角形 (加上紅色)

    const message = hint
      ? `您確定要刪除「${hint}」這筆資料嗎？此動作無法復原。`
      : '您確定要刪除這筆資料嗎？此動作無法復原。';

    this.confirm(
      '刪除確認',
      message,
      warningIcon,
      acceptCallback,
      hint ? { hint } : undefined,
      '確認刪除', // 🌟 UX 優化：明確的破壞性動作提示
      rejectCallback,
      '取消', // 🌟 UX 優化
      dangerBtnClass,
      textBtnClass,
    );
  }

  /**
   * 顯示確認變更未儲存的對話框。
   *
   * @param acceptCallback 按下「離開」後執行的 callback 函式
   * @param rejectCallback (可選) 按下「取消」後執行的 callback 函式
   */
  confirmUnsaved(acceptCallback: () => void, rejectCallback?: () => void): void {
    this.confirm(
      '確認',
      '您確定要離開嗎？尚未儲存的變更將會遺失。',
      'pi pi-info-circle',
      acceptCallback,
      undefined,
      '離開',
      rejectCallback,
      '取消'
    );
  }

  /**
   * 顯示確認放棄未儲存的對話框。
   *
   * @param acceptCallback 按下「放棄」後執行的 callback 函式
   * @param rejectCallback (可選) 按下「取消」後執行的 callback 函式
   */
  confirmDiscardChanges(acceptCallback: () => void, rejectCallback?: () => void): void {
    this.confirm(
      '確認',
      '您確定要放棄所有未儲存的變更嗎？',
      'pi pi-info-circle',
      acceptCallback,
      undefined,
      '放棄',
      rejectCallback,
      '取消'
    );
  }

  /**
   * 共用的底層確認對話框方法。
   *
   * @param header 對話框的標題 (Header)
   * @param message 要確認的訊息內容
   * @param iconClass 訊息前 icon 的 CSS style class
   * @param acceptCallback 按下確認後執行的 callback 函式
   * @param parameters (未使用，可作為擴充用途) 要傳遞的參數
   * @param acceptLabel 接受按鈕的文字
   * @param rejectCallback 按下取消後執行的 callback 函式
   * @param rejectLabel 拒絕按鈕的文字
   * @param acceptButtonStyleClass 確認按鈕的自訂樣式
   * @param rejectButtonStyleClass 取消按鈕的自訂樣式
   */
  confirm(
    header: string,
    message: string,
    iconClass: string,
    acceptCallback: () => void,
    parameters?: Object,
    acceptLabel?: string,
    rejectCallback?: () => void,
    rejectLabel?: string,
    acceptButtonStyleClass?: string, // 🌟 新增：確認按鈕的樣式
    rejectButtonStyleClass?: string, // 🌟 新增：取消按鈕的樣式
  ): void {
    this.confirmationService.confirm({
      message: message,
      header: header,
      icon: iconClass,
      acceptLabel: acceptLabel,
      rejectLabel: rejectLabel,
      acceptButtonStyleClass: acceptButtonStyleClass, // 🌟 綁定設定
      rejectButtonStyleClass: rejectButtonStyleClass, // 🌟 綁定設定
      accept: () => {
        acceptCallback();
      },
      reject: () => {
        if (rejectCallback) {
          rejectCallback();
        }
      },
    });
  }
}
