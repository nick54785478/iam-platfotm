package com.example.demo.iface.rest;

import com.example.demo.application.service.DepartmentQueryService;
import com.example.demo.application.shared.dto.DepartmentHierarchyGottenResult;
import com.example.demo.application.shared.dto.DepartmentRootGottenResult;
import com.example.demo.application.shared.dto.PageQueriedResult;
import com.example.demo.application.shared.query.GetDepartmentRootQuery;
import com.example.demo.iface.dto.res.DepartmentHierarchyGottenResource;
import com.example.demo.iface.dto.res.DepartmentRootsGottenResource;
import com.example.demo.iface.dto.res.DepartmentsSearchedResource;
import com.example.demo.iface.dto.res.FlatDepartmentsGottenResource;
import com.example.demo.iface.dto.res.UserDepartmentTreeGottenResource;
import com.example.demo.infra.shared.dto.DepartmentFlatNodeGottenView;
import com.example.demo.infra.shared.dto.DepartmentTreeNodeGottenView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Department Query Controller (基礎設施層 - 部門讀取端 API 控制器)
 *
 * <pre>
 * 遵循 CQRS (命令查詢職責分離) 架構規範，專責暴露並處理所有面向讀取端、高併發、低延遲的部門視圖查詢請求。
 *
 * <b>極致效能與架構純潔性：</b> 與 Command 寫入端截然不同，本控制器直接向 {@link DepartmentQueryService} 發出請求，
 * 底層技術繞過任何複雜、帶鎖、充血的聚合根領域邏輯，直接從專為讀取優化的 department_views 與  department_tree 投影表中快查資料。
 *
 * 享受純單表或單次高效 JOIN 的極致讀取效能。
 * </pre>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/departments")
public class DepartmentQueryController {

    /**
     * 注入專注唯讀端組裝與排序的查詢應用服務
     */
    private final DepartmentQueryService queryService;

    /**
     * 1. 取得指定部門的完整 N 階層巢狀樹狀結構 (Tree View)。
     *
     * <pre>
     * <b>API 通道:</b> {@code GET /departments/{tenantId}/{id}/tree} <br>
     * <b>主要用途:</b> 專門供應前端組織圖組件（Organization Chart / Tree Viewer）渲染全視圖使用。
     *
     * 會以單次 SQL 連鎖撈出該節點下的所有子孫，並在 Java 記憶體中高速裝配成帶有  children 的遞迴結構，內含預先滾動結算完畢的直屬與全體總人數統計。
     * </pre>
     *
     * @param tenantId 租戶識別碼 (做為第一級隔離維度，URL 路徑參數)
     * @param id       樹根起點部門 ID (將以此節點為根，向下挖出整棵幾何子樹)
     * @return 夾帶多層級巢狀 DTO 結構的樹狀表現層資源
     */
    @GetMapping("/{tenantId}/{id}/tree")
    public ResponseEntity<DepartmentTreeNodeGottenView> getTree(@PathVariable String tenantId, @PathVariable String id,
                                                                @RequestParam(required = false, defaultValue = "false") boolean includeDisabled) {
        // 將 Flag 往下傳遞給 Application Service
        DepartmentTreeNodeGottenView tree = queryService.getTree(tenantId, id, includeDisabled);
        return ResponseEntity.ok(tree);
    }

    /**
     * 2. 取得直系祖先麵包屑導覽路徑 (Breadcrumbs 路徑追溯)。
     *
     * <pre>
     * <b>API 通道:</b> {@code GET /departments/{tenantId}/{id}/breadcrumbs} <br>
     * <b>主要用途:</b> 供應前端系統頂部導覽列渲染路徑痕跡（例如「總公司 > 研發處 > 後端二課」）。
     * 底層透過幾何閉包表反向向上撈取，回傳的列表已依據深度（Depth）進行由高至低的精確打平排序。
     * </pre>
     *
     * @param tenantId 租戶識別碼
     * @param id       當前所在的終點部門 ID (以此為起點向上追溯)
     * @return 經由資料庫打平、順序為 Root -> Parent -> Self 的輕量扁平部門清單資源
     */
    @GetMapping("/{tenantId}/{id}/breadcrumbs")
    public ResponseEntity<FlatDepartmentsGottenResource> getBreadcrumbs(@PathVariable String tenantId,
                                                                        @PathVariable String id) {
        List<DepartmentFlatNodeGottenView> data = queryService.getBreadcrumbPath(tenantId, id);
        return ResponseEntity.ok(new FlatDepartmentsGottenResource("200", "Success", data));
    }

    /**
     * 3. 跨層級全域關鍵字模糊搜尋部門 (Auto-complete 快速搜尋)。
     *
     * <pre>
     * <b>API 通道:</b> {@code GET /departments/{tenantId}/search?keyword=後端} <br>
     * <b>主要用途:</b> 供應前端各表單中的下拉選單（Dropdown Item Selector）或全域彈窗關鍵字快速檢索。 支援同時針對「部門業務代碼
     * (Code)」與「部門顯示名稱 (Name)」進行跨級別模糊比對。
     * </pre>
     *
     * @param tenantId 租戶識別碼
     * @param keyword  搜尋關鍵字 (透過 URL Query Parameter 傳遞，內部具備防禦性空值校驗以保護 DB)
     * @return 滿足模糊比對條件的打平部門清單 (為維護分頁吞吐率，硬性限制最高前 50 筆)
     */
    @GetMapping("/{tenantId}/search")
    public ResponseEntity<DepartmentsSearchedResource> searchDepartments(@PathVariable String tenantId,
                                                                         @RequestParam String keyword) {
        List<DepartmentFlatNodeGottenView> data = queryService.searchDepartments(tenantId, keyword);
        return ResponseEntity.ok(new DepartmentsSearchedResource("200", "Success", data));
    }

    /**
     * 4. 取得特定部門的上下層階層關係與人員編制脈絡 (主管與下屬架構)
     *
     * @param tenantId     多租戶識別碼 (Header 注入)
     * @param departmentId 目標查詢的部門 ID (Path Variable)
     */
    @GetMapping("/{departmentId}/hierarchy")
    public ResponseEntity<DepartmentHierarchyGottenResource> getDepartmentHierarchy(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable String departmentId) {

        DepartmentHierarchyGottenResult hierarchy = queryService.getDepartmentHierarchy(tenantId, departmentId);
        return ResponseEntity.ok(new DepartmentHierarchyGottenResource("200", "Success", hierarchy));
    }

    /**
     * <b>【分頁 API】查詢指定租戶的頂層根部門列表</b>
     *
     * @param tenantId 從 API 網關強制透傳的租戶 Header (防禦 IDOR)
     * @param code     (Optional) 組織代碼 - 精準 EQUAL 查詢
     * @param name     (Optional) 組織名稱 - 模糊 LIKE 查詢
     * @param page     頁碼偏移量 (0 代表第一頁，預設 0)
     * @param size     單頁容量限額 (預設 10 筆)
     * @return 200 OK 內含標準分頁元數據的 JSON 視圖
     */
    @GetMapping("/roots")
    public ResponseEntity<DepartmentRootsGottenResource> getTenantRoots(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        // 1. 裝配查詢指令 (Query Object 內建基本防禦與預設值處理)
        GetDepartmentRootQuery query = new GetDepartmentRootQuery(tenantId, code, name, page, size);

        // 2. 驅動應用層服務進行查詢
        PageQueriedResult<DepartmentRootGottenResult> result = queryService.getTenantRootNodes(query);

        // 3. 回傳標準 HTTP 200 回應
        return ResponseEntity.ok(new DepartmentRootsGottenResource("200", "Success", result));
    }

    /**
     * <b>獲取指定使用者的部門樹狀結構 (包含所屬部門與轄下所有子樹)</b>
     * <p>
     * <b>使用情境：</b> 前端登入後，透過此 API 取得該同仁可視的組織架構範圍，
     * 並利用回傳的 children 屬性遞迴渲染樹狀 UI (Tree UI) 或麵包屑導航。
     * </p>
     *
     * @param tenantId   租戶識別碼 (由 Spring Cloud Gateway 解析 JWT 後強制注入，具備絕對信任度)
     * @param employeeId 欲查詢的目標使用者帳號/工號 (路徑參數)
     * @return {@code 200 OK} 夾帶已於應用層組裝完畢的 O(1) 樹狀視圖陣列 (若無資料則回傳空陣列)
     */
    @GetMapping("/users/{employeeId}/tree")
    public ResponseEntity<UserDepartmentTreeGottenResource> getUserDepartmentTree(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @PathVariable("employeeId") String employeeId) {

        log.info("[CQRS-Query] 接收到獲取使用者部門樹請求, Tenant: {}, Employee: {}", tenantId, employeeId);

        // 呼叫 Application Layer 取得已組裝完成的樹狀視圖
        List<DepartmentTreeNodeGottenView> tree = queryService.getUserDepartmentTree(tenantId, employeeId);

        return ResponseEntity.ok(new UserDepartmentTreeGottenResource("200", "Success", tree));
    }
}