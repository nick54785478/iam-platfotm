package com.example.demo.iface.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * <h2>[網關層 - 全局過濾器] 分布式追蹤碼 (TraceId) 守門員</h2>
 * <p><b>【核心職責】</b>：全網關第一道防線。負責生成或接收 TraceId，綁定日誌 MDC，並下穿給微服務。</p>
 */
@Component
public class TraceIdGatewayFilter extends OncePerRequestFilter implements Ordered {

    // 企業級標準 TraceId Header 命名 (也可以用 W3C 標準的 traceparent)
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    // SLF4J MDC 裡的變數名稱
    private static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 嘗試從前端或上一層反向代理獲取 TraceId
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 2. 如果沒有，網關作為流量入口，主動生成一個全域唯一的 ID (去掉 UUID 的橫槓)
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        // 3. ⚡ 將 TraceId 通電注入 SLF4J 的 MDC (Mapped Diagnostic Context) 中
        // 這樣接下來這條執行緒印出的所有 log 都會自動帶上這個 ID
        MDC.put(MDC_TRACE_ID_KEY, traceId);

        try {
            // 4. 🔥 權限下穿黑科技：包裝 Request，強行把 TraceId 塞進 Header 準備傳給下游
            MutableHttpServletRequest mutableRequest = new MutableHttpServletRequest(request);
            mutableRequest.putHeader(TRACE_ID_HEADER, traceId);

            // 5. 放行給下一道 Filter (如你的 JwtGatewaySecurityFilter)
            filterChain.doFilter(mutableRequest, response);

        } finally {
            // 6. 🧹 安全閉環：請求結束後，務必清除 MDC！
            // 因為 Tomcat 的執行緒是放在 ThreadPool 裡重複使用的，不清掉會導致 ID 污染下一個請求
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }

    @Override
    public int getOrder() {
        // 確保它是全網關【最先】執行的 Filter (比 JWT Filter 的 HIGHEST_PRECEDENCE 還要早)
        return Ordered.HIGHEST_PRECEDENCE - 10;
    }

    // =========================================================================
    // 內部類別：HttpServletRequestWrapper (如果你的 JwtFilter 裡也有這個，
    // 建議未來可以把它抽成一個共用的 Utility 類別，這裡為了獨立運作先放著)
    // =========================================================================
    private static class MutableHttpServletRequest extends HttpServletRequestWrapper {
        private final Map<String, String> customHeaders;

        public MutableHttpServletRequest(HttpServletRequest request) {
            super(request);
            this.customHeaders = new HashMap<>();
        }

        public void putHeader(String name, String value) {
            this.customHeaders.put(name, value);
        }

        @Override
        public String getHeader(String name) {
            String headerValue = customHeaders.get(name);
            return (headerValue != null) ? headerValue : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> set = new HashSet<>(customHeaders.keySet());
            Enumeration<String> e = super.getHeaderNames();
            while (e.hasMoreElements()) {
                set.add(e.nextElement());
            }
            return Collections.enumeration(set);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String headerValue = customHeaders.get(name);
            if (headerValue != null) {
                return Collections.enumeration(Collections.singletonList(headerValue));
            }
            return super.getHeaders(name);
        }
    }
}