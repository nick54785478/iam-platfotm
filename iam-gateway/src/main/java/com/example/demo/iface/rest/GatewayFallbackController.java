package com.example.demo.iface.rest;

import com.example.demo.iface.dto.GatewayResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * <h2>[網關層 - 本地降級門面] 全局微服務崩潰防守控制器 (強型別重構版)</h2>
 * <p>
 * <b>【職責定位】</b>：<br>
 * 當斷路器（Circuit Breaker）偵測到下游微服務（Auth / Dept）發生崩潰、連線失敗或運算嚴重超時時，
 * 本控制器負責進行就地攔截，並吐出標準化的 {@link GatewayResponse} 結構，守住前端體驗。
 * </p>
 */
@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    /**
     * 部門微服務崩潰時的無狀態安全降級通道。
     * <p>
     * <b>【防禦重點】</b>：<br>
     * 當前端索要組織樹而 8081 癱瘓時，就地回傳完全合法的 {@link Collections#emptyList()} 空陣列，
     * 配合強型別的響應結構，完美防堵前端組件（如樹狀 Tree 組件）因為拿不到陣列而觸發 JavaScript NPE 渲染暴斃。
     * </p>
     */
    @RequestMapping("/department")
    public ResponseEntity<GatewayResponse<List<Object>>> departmentFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GatewayResponse.fallback(
                        "IAM-503-DEPT-MUTED",
                        "部門服務運算超時或暫時不可用，網關已啟動動態安全防護，請稍後再試。",
                        Collections.emptyList() // 注入安全兜底空數據
                ));
    }

    /**
     * 認證中心癱瘓時的降級防線。
     * <p>
     * <b>【防禦重點】</b>：<br>
     * 認證中心處於死機狀態時，拒絕全新鑑權。此處不需要返還任何 data 數據體，
     * 透過 {@code .fail()} 產出的 JSON 將會自動剔除 data 欄位，保持回應的俐落性。
     * </p>
     */
    @RequestMapping("/auth")
    public ResponseEntity<GatewayResponse<Void>> authFallback() {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GatewayResponse.fail(
                        "IAM-503-AUTH-DOWN",
                        "認證授權中心正處於極高負載或維護中，暫時拒絕全新鑑權。"
                ));
    }

    @GetMapping("/tenant")
    public ResponseEntity<GatewayResponse<Void>> tenantFallback() {
       return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(GatewayResponse.fail(
                        "IAM-503-TENANT-MUTED",
                        "平台租戶管理服務暫時不可用，網關已啟動動態降級，請稍後再試。"
                ));
    }
}