package com.example.demo.iface.filter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * <h2>[網關層] 分布式無狀態鑑權與權限下穿全局過濾器 (Attribute 版)</h2>
 * <p>
 * <b>【職責定位】</b>：本元件充當整個微服務叢集的最外圈「護城河守門員」，基於 Servlet Tomcat 容器運作。
 * </p>
 * <p>
 * <b>【架構優化】</b>：<br>
 * 放棄容易被 SCG WebMVC 底層 ProxyExchange 繞過的 HttpServletRequestWrapper。
 * 改將解密後的高價值身分與權限資料存放於 {@code request.setAttribute()}，
 * 交由下游路由矩陣進行原生 Header 轉換，確保 100% 安全穿透。
 * </p>
 */
@Component
public class JwtGatewaySecurityFilter extends OncePerRequestFilter implements Ordered {

    private final Algorithm algorithm;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 定義全局公共豁免白名單
    private static final List<String> PUBLIC_WHITELIST = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/v1/platform/tenants",
            "/api/platform/tenants",
            "/actuator/prometheus",
            "/actuator/health",
            "/h2-console/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-resources/**",
            "/webjars/**"
    );

    public JwtGatewaySecurityFilter(@Value("${app.jwt.secret}") String secret) {
        this.algorithm = Algorithm.HMAC256(secret);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. 執行白名單校驗
        boolean isWhitelisted = PUBLIC_WHITELIST.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isWhitelisted) {
            logger.debug("[SCG] 公共白名單通道放行：" + path);
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 拔取 HTTP Authorization 標頭
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            respondWith401(response, "Missing or invalid Authorization header.");
            return;
        }

        String token = authHeader.substring(7);

        try {
            // 3. 執行自解密與合約校準
            DecodedJWT jwt = JWT.require(algorithm).build().verify(token);

            String username = jwt.getSubject();
            String tenantId = jwt.getClaim("tenant").asString();
            List<String> authorities = jwt.getClaim("authorities").asList(String.class);

            // 4. 【核心黑科技：透過 Attribute 傳遞給 RouterFunction】
            request.setAttribute("X-User-Id", username);
            request.setAttribute("X-Tenant-Id", tenantId);

            System.out.println("authorities: "+authorities);
            if (authorities != null && !authorities.isEmpty()) {
                request.setAttribute("X-User-Permissions", String.join(",", authorities));
            } else {
                request.setAttribute("X-User-Permissions", "");
            }

            // 5. 直接放行原始 request
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            respondWith401(response, "Token verification failed or expired. Reason: " + e.getMessage());
        }
    }

    private void respondWith401(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(String.format("{\"error\": \"%s\"}", message));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}