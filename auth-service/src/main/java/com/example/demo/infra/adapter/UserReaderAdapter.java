package com.example.demo.infra.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.example.demo.application.port.UserReaderPort;
import com.example.demo.application.shared.dto.UserRepresentation;
import com.example.demo.infra.projection.repository.SpringDataUserViewRepository;
import com.example.demo.infra.projection.view.UserView;

@Component
class UserReaderAdapter implements UserReaderPort {

	private final SpringDataUserViewRepository viewRepository;

	public UserReaderAdapter(SpringDataUserViewRepository viewRepository) {
		this.viewRepository = viewRepository;
	}

	@Override
	public Optional<UserRepresentation> fetchByUsername(String tenantId, String username) {
		// 🚀 基礎設施層同步改用 username 自資料庫的 user_view 表進行查找
		return viewRepository.findByTenantIdAndUsername(tenantId, username).map(this::toRepresentation);
	}

	@Override
	public List<UserRepresentation> fetchAllByTenant(String tenantId) {
		return viewRepository.findByTenantId(tenantId).stream().map(this::toRepresentation).toList();
	}

	private UserRepresentation toRepresentation(UserView view) {
		return new UserRepresentation(view.getId().toString(), // DTO 內依然保留 id 供前端當作 key 使用，但查詢過程全走 username
				view.getUsername(), view.getEmail(), view.getStatus(), view.getRolesAsSet());
	}

}
