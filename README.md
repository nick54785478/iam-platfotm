# Enterprise IAM & Org-Structure Ecosystem

(IAM 與企業級組織架構微服務生態系統)

本專案是一個基於 戰術領域驅動設計 (Tactical DDD)、CQRS (命令查詢職責分離) 與 事件溯源 (Event Sourcing) 建構的高併發 SaaS 企業級身分與組織樹管理系統。
系統徹底摒棄了傳統 CRUD 與貧血模型，全面擁抱 六角架構 (Hexagonal Architecture)，並透過 Outbox Pattern (發件箱模式) 實現跨庫最終一致性，兼顧技術純粹性與極致效能。

## 系統全局架構 (System Architecture)
系統由一個 API 網關與兩個核心微服務群組成，底層依賴 PostgreSQL、Redis 及高效率的 Kafka KRaft 叢集：

                          [ Client Requests ]
                                  │
                                  ▼
                    ┌───────────────────────────┐
                    │   Spring Cloud Gateway    │ (WebFlux 異步網關 / Redis 限流)
                    └─────────────┬─────────────┘
                                  │
                  ┌───────────────┴───────────────┐
                  ▼                               ▼
       ┌─────────────────────┐         ┌─────────────────────┐
       │   DeptTreeService   │         │     AuthService     │
       │   (Command Side)    │         │  (Command/Query)    │
       └──────────┬──────────┘         └──────────▲──────────┘
                  │                               │
           (Outbox 發送事件)                 (Kafka 監聽)
                  │                               │
                  └────────► [ iam-kafka ] ───────┘
                          (KRaft 叢集事件總線)


**基礎設施矩陣 (Infrastructure Matrix)**

* API 網關層：基於 Spring Cloud Gateway 提供高效能、非阻塞的路由調度與認證安全防禦。

* 消息總線：採用 Kafka 3.6 (KRaft 模式)，徹底擺脫 Zookeeper 依賴，提供高吞吐的事件發布與訂閱。

* 核心快取與限流：Redis 7.0 專職負責網關令牌快取、分散式限流與狀態高速暫存。

* 物理持久化：PostgreSQL 15，各微服務採獨立資料庫 Schema 隔離，全面阻斷跨庫強關聯耦合。


## 微服務組件深度剖析 (Microservices Breakdown)

**1. Spring Cloud Gateway (全域流量網關)**
作為整個微服務叢集的唯一入口，定位為「無狀態、高效能流量調度與安全護城河」。

**核心職責：** 
>* 整合 Redis 實施基於令牌桶演算法（Token Bucket）的精準分散式限流。
>* 阻斷非法請求，負責下游 JWT 令牌的初步合法性校验，並從中萃取租戶標識（tenant_id）進行上下文傳播（Context Propagation）。

**2. DeptTreeService (企業級組織樹與權限大腦)**
本專案旨在解決大型企業面臨的複雜「組織架構重組」、「無限層級樹狀結構管理」以及「精確稽核軌跡」等痛點。將 CQRS 與事件溯源推進到極致。

**戰術 DDD 與輕量化聚合根 (Lightweight Aggregate)：**
>* Department 聚合根刻意不包含任何 List 實體關聯。人員編制指派僅透過輕量級計數器 (activeEmployeeCount) 進行 $O(1)$ 的領域不變量防護，徹底消滅大型部門異動時的資料庫併發死鎖 (Lock Contention)。
>* 充血模型封裝了所有狀態流轉邏輯 (moveTo, mergeInto, restore)，外部系統無法繞過聚合根直接修改狀態。

**極致 CQRS 與事件折疊 (Event Folding)：**
> 100% 寫入端純潔性：處理如「部門合併」等重量級流程時，系統透過 命令端記憶體投影 (Command-Side In-Memory Projection)，重播並折疊歷史事件，在記憶體中推演出當下的人員名單，達到極致效能與強一致性。

**事件溯源 (Event Sourcing) 與時光機：**
>* 單一真實來源 (SSOT)：所有業務狀態改變皆化為不可變的 DomainEvent。提供如 DepartmentMergedEvent 等高含金量事件，明確標示組織資產流向。
>* 時光機還原 (Undelete & Restore)：支援從邏輯刪除狀態中滿血復活，並包含自動回歸頂層 Root 的拓撲防禦機制，防止產生幽靈孤兒節點。

**閉包表拓撲引擎 (Closure Table Topology)：**
> 讀取端投影器異步維護 department_tree 閉包表。不論是查詢整棵子樹、祖先血脈，還是循環圖檢測，皆能在單次 SQL 查詢內完成，徹底擺脫遞迴查詢夢魘。

**3. AuthService (身分驗證與權限視圖)**
本服務專職處理用戶認證、角色指派、JWT 簽發，同時作為權限字典的 Query Side Projection (讀取視圖端)。

**核心職責：**
> 身分驗證與會話維護，以及角色與權限的映射綁定矩陣。

**CQRS 充血模型非同步投影：**
>* 異步監聽來自 DeptTreeService 的權限異動事件。
>* 徹底淘汰傳統的中介 UseCase，由介面層的 PermissionEventHandler 直接調用具備強烈業務語意與防禦校驗的充血實體 PermissionDictView。
>* 內建高水位線版本防禦（邏輯時鐘），徹底免疫 Kafka 因網路重發導致的「重複消費」與分區延遲導致的「亂序到達」災難。

## 系統目錄結構 (Hexagonal Architecture View)

系統嚴格遵守六角架構（Ports and Adapters），以下為標準模組化結構範例：




## 開發指南與架構底線規範 (Development Norms)
為了維持系統的純潔性與穩定性，開發團隊必須嚴守以下底線：

* Event Immutable Rule (事件不可變定律)：所有的 DomainEvent 必須是不可變的，且必須包含 tenantId、發生時間戳記與 version (樂觀鎖水位線)。

* Cross-Aggregate Operations (跨聚合操作守則)：若業務邏輯跨越兩個以上的 Aggregate，絕對禁止在 Aggregate 內部互相調用實體，必須透過 Application 層的 Service 進行 Orchestration (流程編排)。

* Read-Model Segregation (讀寫模型絕對隔離)：任何帶有 _view 或讀取專用的查詢實體與 SQL，絕對禁止注入到寫入端 (Domain 或是 Application 的 Command Service) 中。

* Outbox Pattern 鎖定機制：利用資料庫原生原子更新實作 distributed_locks，確保多實體橫向擴展部署時，同一個時間片段內絕對只會由一台機器的單一線程爭搶執行 Outbox 輪詢，杜絕併發重複發送。
