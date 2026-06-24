import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

/**
 * Loading Mask 服務
 * 用於控制全域載入中遮罩的顯示與隱藏
 *
 * @export
 * @class LoadingMaskService
 */
@Injectable({
  providedIn: 'root',
})
export class LoadingMaskService {
  // 將 BehaviorSubject 設為 private，避免外部直接呼叫 next() 破壞封裝
  private statusSubject = new BehaviorSubject<boolean>(false);
  
  // 曝露唯讀的 Observable 供外部元件訂閱狀態
  public readonly status$: Observable<boolean> = this.statusSubject.asObservable();

  constructor() {}

  /**
   * 開啟 Loading Mask (顯示載入中遮罩)
   */
  show(): void {
    this.statusSubject.next(true);
  }

  /**
   * 隱藏 Loading Mask (關閉載入中遮罩)
   */
  hide(): void {
    this.statusSubject.next(false);
  }
}
