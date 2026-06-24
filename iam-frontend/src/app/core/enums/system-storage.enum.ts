/**
 * 系統儲存資料至 LocalStorage / SessionStorage 所使用的 Key 值列舉 (Enum)。
 * 集中管理所有的 Storage Keys 避免拼寫錯誤與重複定義。
 */
export enum SystemStorageKey {
  /** 使用者登入的 JWT Token */
  JWT_TOKEN = 'token',

  /** 使用者帳號 (登入名稱) */
  USERNAME = 'username',

  /** 租戶 (Tenant) 代碼或識別碼 */
  TENANT = 'tenant',

  /** 使用者顯示名稱 */
  NAME = 'name',

  /** 用於更新 Token 的 Refresh Token */
  REFRESH_TOKEN = 'refreshToken',

  /** 登入後預期導向的 URL (Redirect URL) */
  REDIRECT_URL = 'redirectUrl',

  /** 登入前或導向前夾帶的 Query Parameters */
  QUERY_PARAMS = 'queryParams',

  /** 使用者的權限清單 (通常存放於 SessionStorage) */
  PERMISSIONS = 'permissions',

  /** 紀錄使用者最後操作的專案 ID */
  LAST_PROJECT_ID = 'last_project_id',
}
