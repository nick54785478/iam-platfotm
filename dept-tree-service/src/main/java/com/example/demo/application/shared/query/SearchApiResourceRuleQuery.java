package com.example.demo.application.shared.query;

/**
 * <h2>[應用層 - 讀取端] API 規則動態查詢條件 (Query Object)</h2>
 * <p>
 * <b>【架構定位】</b>：<br>
 * 作為 Controller 接收前端動態搜尋參數的載體，並將其透傳至基礎設施層。
 * 底層的基礎設施適配器會將此物件交由 JpaSpecificationExecutor，
 * 動態組裝成高效的 SQL WHERE 條件。
 * </p>
 */
public record SearchApiResourceRuleQuery(

        /**
         * 租戶識別碼 (精確匹配 EQUAL)
         * <p>例如: "SYSTEM" 或特定客戶代碼</p>
         */
        String tenantId,

        /**
         * HTTP 請求動詞 (精確匹配 EQUAL)
         * <p>例如: "GET", "POST", "PUT", "DELETE", 或萬用字元 "*"</p>
         */
        String httpMethod,

        /**
         * API 路徑特徵 (模糊匹配 LIKE)
         * <p>例如: 搜尋 "dept" 即可涵蓋 "/api/departments/**" 等路徑</p>
         */
        String pathPattern

) {}