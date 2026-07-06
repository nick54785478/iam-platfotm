# 企業級身分與組織架構管理系統 (Enterprise IAM & Org-Structure Ecosystem)

## 專案概述 (Project Overview)
本專案為一款解決大型企業痛點的高併發 SaaS 企業級身分與組織樹管理系統。專案基於**戰術領域驅動設計 (Tactical DDD)**、**CQRS (命令查詢職責分離)** 與 **事件溯源 (Event Sourcing)** 建構。徹底摒棄傳統 CRUD 與貧血模型，全面採用**六角架構 (Hexagonal Architecture)**，並透過 **Outbox Pattern (發件箱模式)** 實現跨庫最終一致性，完美兼顧領域模型的純粹性與高併發場景下的極致效能。

## 核心技術棧 (Tech Stack & Dependencies)
專案全面採用現代化、穩定的高版本技術，以支撐企業級應用的可維護性與擴展性：

* **核心後端框架**：Java 21, Spring Boot 3.2.5, Spring Cloud 2023.0.1 (Gateway)
* **前端框架與 UI**：Angular 18.2, TypeScript 5.5, RxJS 7.8, PrimeNG 17, PrimeFlex 4.0
* **核心資料庫**：PostgreSQL 15 (多 Schema 物理隔離)
* **快取與限流機制**：Redis 7.0 + Redisson 3.27.2 (分散式鎖與 Token Bucket)
* **消息總線 (EDA)**：Kafka 3.6 (KRaft 模式)
* **安全與其他**：JJWT 0.12.5, MapStruct 1.5.5

---

## 系統全局架構藍圖 (System Architecture Blueprint)

以下為本平台的微服務生態拓撲圖，展示了從前端到網關，再到後端各核心領域服務與事件總線的完整資料流向：

```mermaid
flowchart TB
    %% ==========================================
    %% Client & Gateway Layer
    %% ==========================================
    subgraph Frontend ["前端呈現層 (Frontend Layer)"]
        Angular["Angular 18 SPA\n(PrimeNG / RxJS)"]
    end

    subgraph GatewayLayer ["API 網關層 (Gateway Layer)"]
        Gateway["Spring Cloud Gateway\n(無狀態 / 異步轉發 / 校驗 JWT)"]
        Redis[(Redis 7.0 + Redisson)\n(令牌桶限流 / Session 快取)]
        Gateway -.-> |Rate Limiting| Redis
    end
    
    Angular == "HTTPS / REST" ==> Gateway

    %% ==========================================
    %% Core Domain Services Layer
    %% ==========================================
    subgraph Microservices ["核心微服務群 (Core Domain Services)"]

        %% --- 1. DeptTree Service ---
        subgraph DS ["DeptTree Service (組織與權限大腦)"]
            DS_App["Command Service\n(聚合根狀態流轉 / 記憶體投影)"]
            DS_DB[(PostgreSQL : dept_db\nClosure Table)]
            DS_App --> |"Local TX"| DS_DB
            DS_Outbox{"Outbox 輪詢\n(Redisson 分散式鎖)"}
            DS_DB -.-> DS_Outbox
        end

        %% --- 2. Auth Service ---
        subgraph AS ["Auth Service (認證與讀取視圖)"]
            AS_Query["Auth Query Service\n(JWT 簽發 / 權限 O(1) 快查)"]
            AS_DB[(PostgreSQL : auth_db\nRich Projection View)]
            AS_Proj["PermissionEventHandler\n(CQRS 充血投影器 + 邏輯時鐘)"]
            
            AS_Proj --> |"Upsert (Version Check)"| AS_DB
            AS_Query --> |"Query"| AS_DB
        end
        
        %% --- 3. KYC Service ---
        subgraph KS ["KYC Service (實名合規)"]
            KS_App["KYC Process Service\n(NationalId 等值物件校驗)"]
        end
    end

    Gateway == "Context Propagation\n(X-Tenant-Id)" ==> DS_App
    Gateway == "Context Propagation\n(X-Tenant-Id)" ==> AS_Query
    Gateway == "Context Propagation\n(X-Tenant-Id)" ==> KS_App

    %% ==========================================
    %% Event Bus Layer (EDA)
    %% ==========================================
    subgraph EventBus ["事件總線層 (Event-Driven Architecture)"]
        Kafka{{"Kafka 3.6 (KRaft 模式)\n高吞吐事件總線"}}
    end

    %% Event Publishing
    DS_Outbox == "發布 DeptMergedEvent\nPermissionCreatedEvent" ==> Kafka

    %% Event Consuming
    Kafka -.-> |"訂閱 PermissionCreatedEvent\n(異步投影更新)"| AS_Proj
    Kafka -.-> |"訂閱 KYC 相關事件"| KS_App

    %% Styling
    classDef client fill:#e0f7fa,stroke:#006064,stroke-width:2px;
    classDef gateway fill:#fff9c4,stroke:#f57f17,stroke-width:2px;
    classDef service fill:#e8eaf6,stroke:#283593,stroke-width:2px;
    classDef db fill:#fce4ec,stroke:#880e4f,stroke-width:2px;
    classDef kafka fill:#f1f8e9,stroke:#33691e,stroke-width:2px,stroke-dasharray: 5 5;
    
    class Frontend client;
    class GatewayLayer gateway;
    class DS,AS,KS service;
    class DS_DB,AS_DB db;
    class EventBus kafka;
```

---

## 微服務組件深度剖析 (Microservices Breakdown)

### 1. Spring Cloud Gateway (全域流量網關)
作為整個微服務叢集的唯一入口，定位為「無狀態、高效能流量調度與安全護城河」。
* **精準限流**：整合 Redis 實施基於令牌桶演算法（Token Bucket）的分散式限流。
* **安全防禦與上下文傳播**：負責下游 JWT 令牌合法性校驗，萃取 `tenant_id` 進行微服務間的 Context Propagation。

### 2. DeptTree Service (企業級組織樹與權限大腦)
專注解決企業「組織架構重組」、「無限層級樹管理」難題，將 CQRS 與事件溯源推進到極致。
* **輕量化聚合根 (Lightweight Aggregate)**：`Department` 聚合根刻意不包含 List 關聯，人員指派僅透過計數器 ($O(1)$) 進行領域不變量防護，徹底消滅大型部門異動時的資料庫併發死鎖。
* **記憶體投影與事件折疊 (Event Folding)**：在處理「部門合併」等重型操作時，透過命令端記憶體重播並折疊歷史事件，推演當下狀態。
* **閉包表拓撲引擎 (Closure Table)**：異步維護資料庫閉包表，單次 SQL 即可完成複雜的祖先血脈查詢或循環圖檢測，擺脫遞迴查詢夢魘。

### 3. Auth Service (身分驗證與權限視圖)
處理用戶認證、JWT 簽發，同時作為權限字典的 Query Side Projection (讀取視圖端)。
* ** Strict Clean Architecture**：實作最嚴格的整潔架構，領域層 100% POJO。
* **CQRS 充血模型非同步投影**：異步監聽 Kafka 權限事件，淘汰傳統 UseCase，由 `PermissionEventHandler` 直接調用具強業務語意的充血實體。
* **高水位線防禦 (邏輯時鐘)**：內建版本防禦機制，徹底免疫因 Kafka 網路重發導致的「重複消費」與分區「亂序到達」災難。

### 4. KYC Service (實名認證與合規)
負責高安全級別的用戶身分驗證審查。大量採用 DDD 中的 Value Object (值物件) 設計理念（如 `NationalId` 嚴格校驗邏輯），確保合規領域邏輯的高內聚。

### 5. Shared Kernel (共用安全基礎設施)
作為全域技術標準，以獨立 JAR 包發佈。
* **雙軌聯防動態權限 (Dual-Track Authz)**：內建 `PermissionGuardInterceptor`，第一軌支援 `@RequiresPermission` 硬編碼中斷；第二軌透過 Redis Cache-Aside 進行複雜 AntPath 路徑與租戶客製規則的動態降維比對。
* **嚴格依賴反轉 (DIP)**：內部不包含任何具體 JPA Schema，僅定義抽象輸出埠 (Ports)。

---

## 核心技術亮點與開發規範 (Technical Highlights & Norms)

1. **強一致性的 Outbox Pattern**：
   利用資料庫原生原子更新實作分散式鎖 (Distributed Locks)，確保多實體橫向擴展部署時，同一時間只由單一線程爭搶執行 Outbox 輪詢，杜絕併發重複發送。
2. **Event Sourcing 與時光機機制**：
   單一真實來源 (SSOT)。所有業務狀態改變皆化為不可變且帶有版本號的 `DomainEvent`。支援從邏輯刪除狀態中「滿血復活」，並包含拓撲防禦機制防止產生幽靈孤兒節點。
3. **讀寫模型絕對隔離 (Read-Model Segregation)**：
   任何帶有 `_view` 或讀取專用的查詢實體與 SQL，絕對禁止注入到寫入端 (Command Service) 中。
4. **防禦性編程與實用主義 DDD**：
   針對掛載 JPA 標註的實用主義充血模型，全面封鎖預設建構子 (`@NoArgsConstructor(access = AccessLevel.PROTECTED)`) 並拔除 Setter，強制外部呼叫具備業務意義的方法以改變狀態，堅守 DDD 底線。
5. **現代化 Angular 前端整合**：
   獨立的 `iam-frontend` 採用 Angular 18 結合 PrimeNG 企業級元件，利用 RxJS 強大的響應式資料流處理複雜的樹狀組件非同步載入，並透過 PrimeFlex 提供極致的 UI/UX 體驗。
