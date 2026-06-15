package com.example.demo.infra.adapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.example.demo.application.port.TokenProviderPort;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[基礎設施層 - 安全適配器] JWT 套件供應者適配器 (完全體對齊版)</h2>
 * <p>
 * 遵循源始碼規格：自定義的 List 權限點集合，一律透過 {@code withClaim(String, List<?>)} 方法推入 Payload
 * 中。
 * </p>
 */
@Component
class JwtTokenProviderAdapter implements TokenProviderPort {

	private final Algorithm algorithm;
	private final long expirationInMilliseconds;

	public JwtTokenProviderAdapter(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration-ms}") long expirationInMilliseconds) {
		this.algorithm = Algorithm.HMAC256(secret); // 採用經典對稱雜湊加密
		this.expirationInMilliseconds = expirationInMilliseconds;
	}

	// 檔案：infrastructure.security.adapter.JwtTokenProviderAdapter.java

    @Override
    public String createToken(String username, Set<String> permissionStrings) {
        List<String> rawPermissionCodes = new ArrayList<>(permissionStrings);
        
        // 核心補強：簽發 Token 時，順手從 ThreadLocal 拔出當前合法的租戶 ID
        String currentTenantId = TenantContext.getCurrentTenantId();

        return JWT.create()
                .withSubject(username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationInMilliseconds))
                // 🚀 灌入多租戶自解釋標籤！未來網關解開 Token 當場就能拿到租戶，防禦力拉滿
                .withClaim("tenant", currentTenantId)
                .withClaim("authorities", rawPermissionCodes) 
                .sign(algorithm);
    }
    
	@Override
	public String extractUsername(String token) {
		return JWT.require(algorithm).build().verify(token).getSubject();
	}
}