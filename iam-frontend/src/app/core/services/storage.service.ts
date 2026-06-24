import { isPlatformBrowser } from '@angular/common';
import { Inject, Injectable, PLATFORM_ID } from '@angular/core';
import { SystemStorageKey } from '../enums/system-storage.enum';

/**
 * StorageService (儲存服務)
 * 是對 localStorage 及 sessionStorage 的抽象封裝，以確保程式碼相容多平台。
 * 避免伺服器端渲染 (SSR) 使用 Storage 導致報錯。
 * 內部透過 `isPlatformBrowser` 檢測執行環境。
 */
@Injectable({
  providedIn: 'root',
})
export class StorageService {
  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  /**
   * 寫入 LocalStorage
   * @param key 儲存的鍵值
   * @param value 儲存的字串內容
   */
  setLocalStorageItem(key: string, value: string): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem(key, value);
    }
  }

  /**
   * 取得 LocalStorage 的值
   * (注意：此方法會同時更新 'timestamp' 紀錄最後存取時間)
   * @param key 儲存的鍵值
   * @returns 取得的字串內容，若不存在則回傳空字串，非瀏覽器環境回傳 null
   */
  getLocalStorageItem(key: string): string | null {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.setItem('timestamp', new Date().getTime().toString());
      const value = localStorage.getItem(key);
      return value ?? '';
    }
    return null;
  }

  /**
   * 清除特定 LocalStorage 的值
   * @param key 儲存的鍵值
   */
  removeLocalStorageItem(key: string): void {
    if (isPlatformBrowser(this.platformId)) {
      localStorage.removeItem(key);
    }
  }

  /**
   * 寫入 SessionStorage
   * @param key 儲存的鍵值
   * @param value 儲存的字串內容
   */
  setSessionStorageItem(key: string, value: string): void {
    if (isPlatformBrowser(this.platformId)) {
      sessionStorage.setItem(key, value);
    }
  }

  /**
   * 取得 SessionStorage 的值
   * (注意：此方法會同時更新 'timestamp' 紀錄最後存取時間)
   * @param key 儲存的鍵值
   * @returns 取得的字串內容，若不存在或非瀏覽器環境則回傳空字串
   */
  getSessionStorageItem(key: string): string {
    if (isPlatformBrowser(this.platformId)) {
      sessionStorage.setItem('timestamp', new Date().getTime().toString());
      const value = sessionStorage.getItem(key);
      return value ?? '';
    }
    return '';
  }

  /**
   * 清除特定 SessionStorage 的值
   * @param key 儲存的鍵值
   */
  removeSessionStorageItem(key: string): void {
    if (isPlatformBrowser(this.platformId)) {
      sessionStorage.removeItem(key);
    }
  }

  /**
   * 設置權限清單 (存入 SessionStorage)
   * 會將權限陣列轉換成逗號分隔的字串
   * @param permissions 權限字串陣列
   */
  setPermissionList(permissions: string[]): void {
    if (isPlatformBrowser(this.platformId)) {
      this.setSessionStorageItem(
        SystemStorageKey.PERMISSIONS,
        permissions.join(','),
      );
    }
  }

  /**
   * 取得權限清單
   * 從 SessionStorage 讀取並還原為字串陣列
   * @returns 權限字串陣列，若無則回傳空陣列
   */
  getPermissionList(): string[] {
    if (isPlatformBrowser(this.platformId)) {
      const permissionsStr = this.getSessionStorageItem(SystemStorageKey.PERMISSIONS);
      if (permissionsStr) {
        return permissionsStr.split(',');
      }
    }
    return [];
  }
}
