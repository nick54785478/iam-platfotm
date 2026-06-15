package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.port.PasswordEncoderPort;
import com.example.demo.application.port.RoleWriterPort;
import com.example.demo.application.port.UserWriterPort;
import com.example.demo.application.shared.command.RegisterCommand;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[應用層 - 服務] 前台用戶註冊命令服務</h2>
 */
@Service
@Transactional
public class UserRegisterCommandService {

	private final UserWriterPort userWriterPort;
	private final RoleWriterPort roleWriterPort;
	private final PasswordEncoderPort passwordEncoderPort;

	public UserRegisterCommandService(UserWriterPort userWriterPort, RoleWriterPort roleWriterPort,
			PasswordEncoderPort passwordEncoderPort) {
		this.userWriterPort = userWriterPort;
		this.roleWriterPort = roleWriterPort;
		this.passwordEncoderPort = passwordEncoderPort;
	}

	/**
	 * <b>🚀 核心編排：執行匿名使用者自主註冊流程</b>
	 */
	public void register(RegisterCommand command) {
		try {
			// 1. 核心通電：匿名用戶必須在 Command 中指定他要加入哪一個租戶空間 (Tenant)
			TenantContext.setCurrentTenantId(command.tenantCode());

			// 2. 業務防禦：檢查在該租戶空間內，用戶名是否已經被註冊過
			if (userWriterPort.findByUsername(command.username()).isPresent()) {
				throw new IllegalArgumentException(
						"Username '" + command.username() + "' is already taken in this tenant");
			}

			// 3. 密碼高強度雜湊加密
			String encryptedPassword = passwordEncoderPort.encode(command.rawPassword());

			// 4. 驅動 User 充血工廠方法建立實體 (狀態預設 ACTIVE，或依業務改為 PENDING)
			User newUser = User.createNew(command.username(), encryptedPassword, command.email());

			// 5. 🚨 安全防禦硬核鎖死：自主註冊的用戶，大腦「自動且強制」賦予其最低限度的基礎平民角色 (如 ROLE_USER)
			// 絕對不看、也不接受前端傳進來的 roles 參數，徹底杜絕提權漏洞（Privilege Escalation）
			roleWriterPort.findByRoleCode("ROLE_USER").ifPresent(defaultRole -> {
				newUser.assignRole(defaultRole.getId());
			});

			// 6. 存檔落地（自動同步累積領域事件、打包多租戶信封並激活 Outbox）
			userWriterPort.save(newUser);

		} finally {
			// 7. 安全斷電
			TenantContext.clear();
		}
	}
}
