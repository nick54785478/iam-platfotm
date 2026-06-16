package com.example.demo.iface.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * <h2>[微服務層] 分布式追蹤碼 (TraceId) 接收器</h2>
 */
@Component
public class TraceIdReceiverFilter extends OncePerRequestFilter implements Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 盲目信任並接收網關傳來的 TraceId
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // 如果連網關都沒傳，做個 fallback 兜底
        if (traceId == null || traceId.isBlank()) {
            traceId = "UNKNOWN-" + System.currentTimeMillis();
        }

        // 2. 注入 AuthService 當前執行緒的 MDC
        MDC.put(MDC_TRACE_ID_KEY, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 3. 執行緒結束務必清理
            MDC.remove(MDC_TRACE_ID_KEY);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 確保在 AuthService 也是最先執行
    }
}