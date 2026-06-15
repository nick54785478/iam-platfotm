package com.example.demo.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.application.domain.group.aggregate.Group;
import com.example.demo.application.domain.role.aggregate.Role;
import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.port.GroupWriterPort;
import com.example.demo.application.port.RoleWriterPort;
import com.example.demo.application.port.UserWriterPort;

/**
 * <h2>[應用層 - 服務] 群組命令編排服務 (Group Command Service)</h2>
 * <p>
 * <b>【核心職責】</b>：<br>
 * 本類別為寫入側（Command Side）的群組業務大腦。負責編排群組的建立、更名、成員調配與角色賦予流程。
 * 它不包含核心業務規則，而是扮演跨聚合根（Group、User、Role）的協調與元數據轉譯官（將業務 Code 換成物理 UUID）， 最後驅動
 * Group 聚合根做出充血變更並閉環存檔。
 * </p>
 */
@Service
@Transactional // 🚀 啟動寫入事務，保障群組業務與 Outbox 事件落地的原子性
public class GroupCommandService {

	private final GroupWriterPort groupWriterPort;
	private final UserWriterPort userWriterPort;
	private final RoleWriterPort roleWriterPort;

	public GroupCommandService(GroupWriterPort groupWriterPort, UserWriterPort userWriterPort,
			RoleWriterPort roleWriterPort) {
		this.groupWriterPort = groupWriterPort;
		this.userWriterPort = userWriterPort;
		this.roleWriterPort = roleWriterPort;
	}

	/**
	 * <b>建立全新自定義使用者群組</b>
	 * <p>
	 * 守護業務規則：在當前隔離租戶內，群組的 groupCode 必須絕對唯一。
	 * </p>
	 */
	public void createGroup(String groupName, String groupCode) {
		// 1. 業務規則防禦：群組代碼不可重複
		if (groupWriterPort.findByGroupCode(groupCode).isPresent()) {
			throw new IllegalArgumentException("Group code '" + groupCode + "' already exists in current tenant");
		}

		// 2. 呼叫充血模型工廠（內部自動註冊 GroupChangedEvent）
		Group group = Group.create(groupName, groupCode);

		// 3. 存檔（透過 Adapter 自動將事件打包信封並引爆全局 Outbox 監聽）
		groupWriterPort.save(group);
	}

	/**
	 * <b>群組更名描述</b>
	 */
	public void renameGroup(String groupCode, String newName) {
		Group group = groupWriterPort.findByGroupCode(groupCode)
				.orElseThrow(() -> new IllegalArgumentException("Group code '" + groupCode + "' not found"));

		group.rename(newName);
		groupWriterPort.save(group);
	}

	/**
	 * <b>🚀 核心編排：將指定使用者加入特定群組 (Add Member)</b>
	 * <p>
	 * <b>【解耦美感】</b>：入參完全以業務主角 username 與 groupCode 為主。 透過 UserPort 撈出物理
	 * UserId，並以「弱引用 ID」的形式灌入 Group 聚合根，捍衛 DDD 邊界。
	 * </p>
	 */
	public void addMemberToGroup(String groupCode, String username) {
		// 1. 撈出群組聚合根
		Group group = groupWriterPort.findByGroupCode(groupCode)
				.orElseThrow(() -> new IllegalArgumentException("Group code '" + groupCode + "' not found"));

		// 2. 跨聚合根元數據還原：透過 username 撈出使用者聚合根，藉此取得物理 UserId
		User user = userWriterPort.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));

		// 3. 驅動群組充血模型行為：將 UserId 弱引用加入成員集合（內部自動去重並註冊變更事件）
		group.addMember(user.getId());

		// 4. 存檔閉環
		groupWriterPort.save(group);
	}

	/**
	 * <b>🚀 核心編排：將指定使用者移出特定群組 (Remove Member)</b>
	 */
	public void removeMemberFromGroup(String groupCode, String username) {
		Group group = groupWriterPort.findByGroupCode(groupCode)
				.orElseThrow(() -> new IllegalArgumentException("Group code '" + groupCode + "' not found"));

		User user = userWriterPort.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User '" + username + "' not found"));

		// 驅動群組移除成員
		group.removeMember(user.getId());

		groupWriterPort.save(group);
	}

	/**
	 * <b>核心編排：將特定角色指派給群組 (Assign Role to Group)</b>
	 * <p>
	 * 一旦指派成功，未來網關層鑑權時，該群組內的所有使用者都將繼承此 roleCode 的全量權限點！
	 * </p>
	 */
	public void assignRoleToGroup(String groupCode, String roleCode) {
		Group group = groupWriterPort.findByGroupCode(groupCode)
				.orElseThrow(() -> new IllegalArgumentException("Group code '" + groupCode + "' not found"));

		// 跨聚合根元數據還原：透過 roleCode 撈出角色聚合根，藉此取得物理 RoleId
		Role role = roleWriterPort.findByRoleCode(roleCode)
				.orElseThrow(() -> new IllegalArgumentException("Role code '" + roleCode + "' not found"));

		// 驅動群組指派角色（弱引用 RoleId 入腹）
		group.assignRole(role.getId());

		groupWriterPort.save(group);
	}

	/**
	 * <b>🚀 核心編排：撤銷特定群組的角色 (Revoke Role from Group)</b>
	 */
	public void revokeRoleFromGroup(String groupCode, String roleCode) {
		Group group = groupWriterPort.findByGroupCode(groupCode)
				.orElseThrow(() -> new IllegalArgumentException("Group code '" + groupCode + "' not found"));

		Role role = roleWriterPort.findByRoleCode(roleCode)
				.orElseThrow(() -> new IllegalArgumentException("Role code '" + roleCode + "' not found"));

		// 驅動群組撤銷角色
		group.revokeRole(role.getId());

		groupWriterPort.save(group);
	}
}