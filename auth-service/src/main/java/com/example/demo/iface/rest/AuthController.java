package com.example.demo.iface.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.service.AuthCommandService;
import com.example.demo.application.service.UserRegisterCommandService;
import com.example.demo.application.shared.command.LoginCommand;
import com.example.demo.application.shared.command.RegisterCommand;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[基礎設施層 - Web 適配器] 認證傳輸控制層 (Auth Controller) - 頂規重整版</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本控制器位於 Spring MVC 的公共綠色通道中（已被 {@code WebMvcConfiguration} 排除在攔截器外）。
 * 負責將前端傳入的匿名登入載荷解碼。它不負責越權調用 {@link TenantContext}， 而是將多租戶代碼封裝進
 * {@link LoginCommand} 遞交給內圈大腦，由大腦內聚控場。
 * </p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthCommandService authCommandService;
	private final UserRegisterCommandService userRegisterCommandService; // 🚀 注入註冊大腦

	public AuthController(AuthCommandService authCommandService,
			UserRegisterCommandService userRegisterCommandService) {
		this.authCommandService = authCommandService;
		this.userRegisterCommandService = userRegisterCommandService;
	}

	/**
	 * <b>全局登入入口：驗證身份並獲取高價值 JWToken</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/auth/login}
	 * </p>
	 * <p>
	 * <b>【架構美感】</b>：由於此路徑為免攔截綠色通道，控制器不再沾染任何 {@code ThreadLocal} 技術細節，
	 * 保持無狀態傳輸本質，防禦職責位移技術債。
	 * </p>
	 */
	@PostMapping("/login")
	public ResponseEntity<JwtTokenResponse> login(@RequestBody LoginRequest request) {
		// 1. 面向規格：組裝完全自包含、顯式攜帶租戶標籤的領域命令
		LoginCommand command = new LoginCommand(request.tenantCode(), request.username(), request.password());

		// 2. 直接推進寫入側大腦，大腦內部會親自通電、處理業務並在結束時完成安全抹除
		String token = authCommandService.loginAndGenerateToken(command);

		// 3. 回應前端 Token 成果大禮包
		return ResponseEntity.ok(new JwtTokenResponse(token, "Bearer"));
	}

	/**
	 * <b>🌱 公共註冊入口：匿名使用者自主加入特定租戶空間</b>
	 * <p>
	 * <b>HTTP 語意：</b> {@code POST /api/auth/register}
	 * </p>
	 */
	@PostMapping("/register")
	public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
		// 轉譯為內圈自包含命令
		RegisterCommand command = new RegisterCommand(request.tenantCode(), request.username(), request.password(),
				request.email());

		// 遞交註冊大腦執行
		userRegisterCommandService.register(command);

		return ResponseEntity.ok().build(); // 200 OK 註冊成功
	}
}

// ── 💡 傳輸層專用不可變原生 Record DTOs ──
record LoginRequest(String tenantCode, String username, String password) {
}

record JwtTokenResponse(String accessToken, String tokenType) {
}

record RegisterRequest(String tenantCode, String username, String password, String email) {
}