package com.example.demo.application.port;

import java.util.List;
import java.util.Optional;

import com.example.demo.application.domain.group.aggregate.Group;
import com.example.demo.application.domain.group.aggregate.vo.GroupId;
import com.example.demo.application.domain.user.aggregate.vo.UserId;

/**
 * <h2>[應用層 - Port 接口] 群組寫入側輸出端口 (Group Writer Port)</h2>
 */
public interface GroupCommandRepositoryPort {
	Optional<Group> findById(GroupId id);

	/**
	 * 規格對齊：完全以當前租戶空間與業務不變鍵 groupCode 進行 O(1) 級別的精準隔離查詢
	 */
	Optional<Group> findByGroupCode(String groupCode);

	void save(Group group);

	void delete(Group group);
	
	/**
     * 🚀 補齊寫入側規格：依據使用者物理 ID，撈出該同仁在寫入側所參與的所有群組充血實體
     * 用於業務大腦在事務邊界內還原群組角色關係。
     */
    List<Group> findGroupsByUserId(UserId userId);
}