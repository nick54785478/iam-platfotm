package com.example.demo.application.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.group.aggregate.Group;
import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.domain.user.aggregate.vo.UserStatus;
import com.example.demo.application.port.GroupCommandRepositoryPort;
import com.example.demo.application.port.PasswordEncoderPort;
import com.example.demo.application.port.RoleCommandRepositoryPort;
import com.example.demo.application.port.TokenProviderPort;
import com.example.demo.application.port.UserCommandRepositoryPort;
import com.example.demo.application.shared.command.LoginCommand;
import com.example.demo.infra.context.TenantContext;

/**
 * <h2>[應用層 - 服務] 認證命令編排服務 (Auth Command Service) - 終極定版</h2>
 * <p>
 * <b>【核心編排美感】</b>：<br>
 * 本服務完全在寫入側（Command宇宙）閉環。它調用 {@link UserCommandRepositoryPort} 查詢與還原充血實體， 透過 {@link User}
 * 內聚的業務方法控制失敗懲罰與歸零，完全不橫向跨界調用 Query 側服務，死守安全邊界。
 * </p>
 */
@Service
@Transactional // 🚀 啟動寫入事務，保障登入失敗計數與狀態變更具備原子性
public class AuthCommandService {

	private final UserCommandRepositoryPort userWriterPort;
	private final GroupCommandRepositoryPort groupWriterPort;
	private final RoleCommandRepositoryPort roleWriterPort;
	private final PasswordEncoderPort passwordEncoderPort;
	private final TokenProviderPort tokenProviderPort;

	public AuthCommandService(UserCommandRepositoryPort userWriterPort, GroupCommandRepositoryPort groupWriterPort,
							  RoleCommandRepositoryPort roleWriterPort, PasswordEncoderPort passwordEncoderPort,
							  TokenProviderPort tokenProviderPort) {
		this.userWriterPort = userWriterPort;
		this.groupWriterPort = groupWriterPort;
		this.roleWriterPort = roleWriterPort;
		this.passwordEncoderPort = passwordEncoderPort;
		this.tokenProviderPort = tokenProviderPort;
	}

	/**
	 * <b>執行身份驗證、充血不變性維護，並在寫入側內聚閉環 Context 生命週期</b>
	 */
	public String loginAndGenerateToken(LoginCommand command) {
		try {
			// 【核心通電點】：由於登入接口豁免了最外圈攔截器，大腦在此親自為空氣管道接通電源！
			// 這樣後續所有的 userWriterPort, groupWriterPort 才能在正確的租戶隔離空間內精準定位 SQL。
			TenantContext.setCurrentTenantId(command.tenantCode());

			// 1. 撈出使用者充血聚合根
			User user = userWriterPort.findByUsername(command.username())
					.orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

			// 2. 充血模型生命週期守護
			if (user.getStatus() == UserStatus.DEACTIVATED) {
				throw new IllegalStateException("Account is deactivated");
			}
			if (user.getStatus() == UserStatus.LOCKED) {
				throw new IllegalStateException("Account is locked due to multiple failed attempts.");
			}

			// 3. 密碼雜湊比對
			boolean isPasswordMatch = passwordEncoderPort.matches(command.rawPassword(),
					user.getAccountInfo().encryptedPassword());

			if (!isPasswordMatch) {
				user.handleFailedLogin();
				userWriterPort.save(user); // 存檔落地計數器
				throw new IllegalArgumentException("Invalid username or password");
			}

			// 歸零失敗計數
			user.handleSuccessfulLogin();
			userWriterPort.save(user);

			// 4. 寫入側元數據收集管線 (不交叉引用，不依賴 Query 側)
			Set<String> personalRoleCodes = roleWriterPort.findRoleCodesByRoleIds(user.getAssignedRoles());
			Set<String> allTargetRoleCodes = new HashSet<>(personalRoleCodes);

			List<Group> userBelongedGroups = groupWriterPort.findGroupsByUserId(user.getId());
			for (Group group : userBelongedGroups) {
				Set<String> inheritedRoleCodes = roleWriterPort.findRoleCodesByRoleIds(group.getAssignedRoleIds());
				allTargetRoleCodes.addAll(inheritedRoleCodes);
			}

			Set<String> finalPermissionStrings = roleWriterPort.findPermissionStringsByRoleCodes(allTargetRoleCodes);

			// 5. 簽發 Token
			return tokenProviderPort.createToken(user.getAccountInfo().username(), finalPermissionStrings);

		} finally {
			// 【硬核安全保險盒】：不論登入成功、密碼打錯、或是帳號被鎖定噴異常，
			// 只要大腦執行結束離開，必須百分之百強迫清除當前執行緒的 Tenant 殘留！
			// 徹底瓦解多租戶在 Web 執行緒池重用時的內存洩漏與跨租戶交叉污染危機。
			TenantContext.clear();
		}
	}
}