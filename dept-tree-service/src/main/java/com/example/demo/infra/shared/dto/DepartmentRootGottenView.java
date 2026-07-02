package com.example.demo.infra.shared.dto;


/**
 * <h2>[基礎設施層] 部門根節點原生 SQL 投影介面 (Interface Projection)</h2>
 * <p>
 * <b>【技術原理】</b>：<br>
 * 這是 Spring Data JPA 的核心黑魔法之一。針對 Native SQL 查詢，我們不需要編寫具體的實作類別，
 * 只需定義與 SQL 查詢結果欄位（或別名）完全對齊的 Getter 方法。<br>
 * 執行期 Spring 會透過動態代理自動注入數據，提供完全型別安全的唯讀 View 物件。
 * </p>
 */
public interface DepartmentRootGottenView {

    String getId();

    String getCode();

    String getName();

    String getStatus();

    // 💡 使用 Integer/Long 封裝，防禦資料庫欄位可能為 Null 的極端邊界狀況
    Integer getSortOrder();

    Integer getDirectHeadcount();

    Integer getTotalHeadcount();
}