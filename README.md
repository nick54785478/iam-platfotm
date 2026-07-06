# Enterprise IAM & Org-Structure Ecosystem

(IAM 與企業級組織架構微服務生態系統)

本專案是一個基於 **戰術領域驅動設計 (Tactical DDD)**、**CQRS (命令查詢職責分離)** 與 **事件溯源 (Event Sourcing)** 建構的高併發 SaaS 企業級身分與組織樹管理系統。
系統徹底摒棄了傳統 CRUD 與貧血模型，全面擁抱 **六角架構 (Hexagonal Architecture)**，並透過 **Outbox Pattern (發件箱模式)** 實現跨庫最終一致性，兼顧技術純粹性與極致效能。

---

## 核心技術生態 (Tech Stack)

### 後端 (Backend)
* **語言與框架**：Java 21, Spring Boot 3.2.5
* **微服務與網關**：Spring Cloud 2023.0.1 (Spring Cloud Gateway)
* **資料庫與快取**：PostgreSQL 15, Redis 7.0 + Redisson 3.27.2
* **消息總線**：Kafka 3.6 (KRaft 模式，無 Zookeeper)
* **安全與其他**：JJWT 0.12.5, MapStruct 1.5.5

### 前端 (Frontend)
* **語言與框架**：Angular 18.2, TypeScript 5.5, RxJS 7.8
* **UI 與樣式**：PrimeNG 17.18, PrimeFlex 4.0, PrimeIcons 7.0

---

## 架構平台拓撲圖 (Architecture Blueprint)

### 簡易系統全局架構 (ASCII 示意圖)
```text
                          [ Client Requests ]
                                   │
                                   ▼
                     ┌───────────────────────────┐
                     │   Spring Cloud Gateway    │ (WebFlux / Redis 限流)
                     └─────────────┬─────────────┘
                                   │
           ┌───────────────────────┬───────┴───────┬───────────────────────┐
           ▼                       ▼               ▼                       ▼
 ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────┐
 │   TenantService   │   │  DeptTreeService  │   │    AuthService    │   │    KycService     │
 │   (SaaS 租戶)     │   │  (Command Side)   │   │  (Command/Query)  │   │   (實名合規)      │
 └─────────┬─────────┘   └─────────┬─────────┘   └─────────▲─────────┘   └─────────▲─────────┘
           │                       │                       │                       │
      (Outbox 事件)           (Outbox 事件)           (Kafka 監聽)            (Kafka 監聽)
           │                       │                       │                       │
           └───────────────────────┴──────► [ iam-kafka ] ◄┴───────────────────────┘
                                         (KRaft 叢集事件總線)
```

### 微服務生態詳細拓撲 (Mermaid)

```mermaid
flowchart TB
    %% ==========================================
    %% Client & Gateway Layer
    %% ==========================================
    subgraph Frontend ["前端呈現層 (Frontend Layer)"]
        Angular["Angular 18 SPA\n(PrimeNG / RxJS)"]
    end

    subgraph GatewayLayer ["API 網關層 (Gateway Layer)"]
        Gateway["Spring Cloud Gateway\n(無狀態 / 異步轉發 / 驗證 JWT)"]
        Redis[(Redis 7.0 + Redisson)\n(令牌桶限流 / Session 快取)]
        Gateway -.-> |Rate Limiting| Redis
    end
    
    Angular == "HTTPS / REST" ==> Gateway

    %% ==========================================
    %% Core Domain Services Layer
    %% ==========================================
    subgraph Microservices ["核心微服務群 (Domain Services)"]
        
        %% --- 1. Tenant Service (Future Core) ---
        subgraph TS ["TenantService (未來的平台樞紐)"]
            TS_App["Tenant Command Service\n(SaaS 租戶入駐 / 計費 / 停權)"]
            TS_DB[(PostgreSQL\ntenant_db)]
            TS_App --> |"Local TX"| TS_DB
        end

        %% --- 2. DeptTree Service (Command/Write Side) ---
        subgraph DS ["DeptTreeService (組織與權限大腦)"]
            DS_App["Department Command Service\n(聚合根 / 狀態流轉 / 拓撲檢查)"]
            DS_DB[(PostgreSQL : dept_db\nClosure Table)]
            DS_App --> |"Local TX"| DS_DB
            DS_Outbox{"Outbox 輪詢\n(Redisson 分散式鎖)"}
            DS_DB -.-> DS_Outbox
        end

        %% --- 3. Auth Service (Read/Query Side + Identity) ---
        subgraph AS ["AuthService (認證與讀取視圖)"]
            AS_Query["Auth Query Service\n(JWT 簽發 / 登入驗證 / 權限檢索)"]
            AS_DB[(PostgreSQL : auth_db\nRich Projection View)]
            AS_Proj["PermissionEventHandler\n(CQRS 充血投影器 + 邏輯時鐘防禦)"]
            
            AS_Proj --> |"Upsert (Version Check)"| AS_DB
            AS_Query --> |"O(1) 快查"| AS_DB
        end
        
        %% --- 4. KYC Service ---
        subgraph KS ["KYC Service (實名合規)"]
            KS_App["KYC Process Service\n(NationalId 等值物件校驗)"]
        end
    end

    Gateway == "Context Propagation\n(X-Tenant-Id)" ==> TS_App
    Gateway == "Context Propagation\n(X-Tenant-Id)" ==> DS_App
    Gateway == "Context Propagation\n(X-Tenant-Id)" ==> AS_Query
    Gateway == "Context Propagation\n(X-Tenant-Id)" ==> KS_App

    %% ==========================================
    %% Event Bus Layer (EDA)
    %% ==========================================
    subgraph EventBus ["事件總線層 (Event-Driven Architecture)"]
        Kafka{{"Kafka 3.6 (KRaft 模式)\n(iam-kafka)"}}
    end

    %% Event Publishing
    DS_Outbox == "發布 DeptMergedEvent\nPermissionCreatedEvent" ==> Kafka

    %% Event Consuming
    Kafka -.-> |"訂閱 PermissionCreatedEvent\n(投影更新)"| AS_Proj
    Kafka -.-> |"訂閱 KYC 與租戶事件"| KS_App

    %% Styling
    classDef client fill:#e0f7fa,stroke:#006064,stroke-width:2px;
    classDef gateway fill:#fff9c4,stroke:#f57f17,stroke-width:2px;
    classDef service fill:#e8eaf6,stroke:#283593,stroke-width:2px;
    classDef db fill:#fce4ec,stroke:#880e4f,stroke-width:2px;
    classDef kafka fill:#f1f8e9,stroke:#33691e,stroke-width:2px,stroke-dasharray: 5 5;
    
    class Frontend client;
    class GatewayLayer gateway;
    class TS,DS,AS,KS service;
    class DS_DB,AS_DB,TS_DB db;
    class EventBus kafka;
```

---

## 微服務組件深度剖析 (Microservices Breakdown)

### 1. Spring Cloud Gateway (全域流量網關)
作為整個微服務叢集的唯一入口，定位為「無狀態、高效能流量調度與安全護城河」。
* **精準分散式限流**：整合 Redis 實施基於令牌桶演算法（Token Bucket）的精準限流。
* **身分與上下文傳播**：阻斷非法請求，負責下游 JWT 令牌的初步合法性校驗，並萃取租戶標識（`tenant_id`）進行上下文傳播（Context Propagation）。

### 2. DeptTreeService (企業級組織樹與權限大腦)
本服務旨在解決大型企業面臨的複雜「組織架構重組」、「無限層級樹狀結構管理」以及「精確稽核軌跡」等痛點。
* **戰術 DDD 與輕量化聚合根 (Lightweight Aggregate)**：`Department` 聚合根刻意不包含任何 List 實體關聯。人員編制指派僅透過輕量級計數器進行 $O(1)$ 的領域不變量防護，徹底消滅大型部門異動時的資料庫併發死鎖 (Lock Contention)。
* **極致 CQRS 與事件折疊 (Event Folding)**：100% 寫入端純潔性。處理如「部門合併」等重量級流程時，系統透過 **命令端記憶體投影 (Command-Side In-Memory Projection)**，重播並折疊歷史事件推演出當下名單，達到極致效能與強一致性。
* **事件溯源 (Event Sourcing) 與時光機**：單一真實來源 (SSOT)。所有業務狀態改變皆化為不可變的 `DomainEvent`，支援從邏輯刪除狀態中滿血復活，並包含自動回歸頂層 Root 的拓撲防禦機制，防止產生幽靈孤兒節點。
* **閉包表拓撲引擎 (Closure Table Topology)**：讀取端投影器異步維護 `department_tree` 閉包表。單次 SQL 查詢內完成子樹查詢、祖先血脈、循環圖檢測，徹底擺脫遞迴查詢夢魘。
* **架構風格**：採用實用主義的充血模型 (Pragmatic DDD)，允許領域實體掛載 JPA 標註換取高效能，但嚴格透過「封鎖預設建構子」與「拔除所有的 Setter」來彌補防禦。

### 3. AuthService (身分驗證與權限視圖)
本服務專職處理用戶認證、角色指派、JWT 簽發，同時作為權限字典的 Query Side Projection (讀取視圖端)。
* **CQRS 充血模型非同步投影**：異步監聽來自 DeptTreeService 的權限異動事件。徹底淘汰傳統的中介 UseCase，由介面層的 `PermissionEventHandler` 直接調用具備強烈業務語意與防禦校驗的充血實體 `PermissionDictView`。
* **高水位線版本防禦**：內建邏輯時鐘，徹底免疫 Kafka 因網路重發導致的「重複消費」與分區延遲導致的「亂序到達」災難。
* **架構風格**：採用 **Strict Clean Architecture**。領域層是 100% 的純 Java (POJO)，絕無任何技術物件 (如 JPA)。

### 4. KYC Service (實名合規服務)
處理高安全等級的用戶身分驗證審查，深入應用 DDD 中的 Value Object 設計（如 `NationalId` 的嚴格校驗），確保合規邏輯高內聚。

### 5. Shared Kernel (共用安全基礎設施)
作為全域的技術防禦標準，以獨立二進位套件 (JAR) 形式發佈，各微服務皆須引入。
* **嚴格依賴反轉 (DIP)**：內部絕不包含任何 JPA Entity 或具體 Schema，僅定義抽象輸出埠。
* **雙軌聯防動態權限 (Dual-Track Authz)**：內建 `PermissionGuardInterceptor`，第一軌支援 `@RequiresPermission` 硬編碼中斷；第二軌透過 Redis Cache-Aside 引擎進行 $O(1)$ 複雜 AntPath 路徑與租戶客製化規則的動態降維比對。

---

## 系統目錄結構 (Hexagonal Architecture View)

各系統嚴格遵守六角架構（Ports and Adapters），標準模組化結構如下：
  
```text
src/main/java/com/example/
├── application/             # Application Layer (Hexagon Inside)
│   ├── service/             # Process Managers & Orchestrators (跨聚合協調)
│   ├── port/                # Ports (Inbound & Outbound Interfaces)
│   ├── domain/              # Domain Layer (Hexagon Center)
│   │   ├── aggregate        # Aggregate (內含 Aggregate Root & 相關 Value Objects)
│   │   ├── event/           # Domain Events
│   │   ├── exception/       # Domain Exceptions
│   │   └── repository/      # Domain Repository Interfaces 
│   └── shared/              # Shared Kernels (Commands, DTOs, Views)
├── infrastructure/          # Infrastructure Layer (Hexagon Outside)
│   ├── adapter/             # Implementations of Ports
│   ├── projection/          # Query Side Projection Logic
│   └── persistence/         # Repositories (JPA)
└── iface/                   # Presentation / Interface Layer
    ├── dto/                 # Request/Response Objects
    ├── rest/                # RESTful Command/Query Endpoints
    ├── schedule/            # Schedule Jobs (Outbox Polling)
    ├── exception/           # Global Exception Handlers
    └── event/               # Event Listeners/Handlers
```

---

## 開發指南與架構底線規範 (Development Norms)

為了維持系統的純潔性與穩定性，開發團隊必須嚴守以下底線：

1. **Event Immutable Rule (事件不可變定律)**：所有的 DomainEvent 必須是不可變的，且必須包含 `tenantId`、發生時間戳記與 `version` (樂觀鎖水位線)。
2. **Cross-Aggregate Operations (跨聚合操作守則)**：若業務邏輯跨越兩個以上的 Aggregate，絕對禁止在 Aggregate 內部互相調用實體，必須透過 Application 層的 Service 進行 Orchestration (流程編排)。
3. **Read-Model Segregation (讀寫模型絕對隔離)**：任何帶有 `_view` 或讀取專用的查詢實體與 SQL，絕對禁止注入到寫入端 (Domain 或是 Application 的 Command Service) 中。
4. **Outbox Pattern 鎖定機制**：利用資料庫原生原子更新或 Redisson 實作分散式鎖 (Distributed Locks)，確保多實體橫向擴展部署時，同一個時間片段內絕對只會由單一線程爭搶執行 Outbox 輪詢，杜絕併發重複發送。

---

## 啟動與安裝指南 (Getting Started)

### 1. 建置 Shared Kernel (後端本地依賴)
系統的核心安全防護網 `shared-kernel` 採獨立二進位發佈模式。初次建置或代碼異動時，請先將其安裝至本地 Maven 儲存庫：

在 `shared-kernel` 專案目錄下執行：
```bash
mvn clean install
```
Maven 會自動將 `shared-kernel-1.0.0-SNAPSHOT.jar` 放入您的本地 `~/.m2/repository` 中。後續各微服務編譯時將自動抓取。

### 2. 啟動後端微服務
確認 Docker 中的基礎設施（PostgreSQL, Redis, Kafka）已啟動，接著依序啟動各微服務的 Spring Boot 主程式：
* `GatewayApplication` (API 網關)
* `AuthApplication` (認證服務)
* `DeptTreeApplication` (組織樹服務)
* `KycApplication` (KYC 服務)

### 3. 啟動前端 (Angular)
進入前端目錄並安裝依賴：
```bash
cd iam-frontend
npm install
npm start # 或 ng serve --proxy-config proxy.conf.json
```
前端伺服器啟動後，即可透過瀏覽器存取。API 請求將自動透過 proxy 導向 Spring Cloud Gateway。