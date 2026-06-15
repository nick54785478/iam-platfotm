package com.example.demo.infra.projection.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.infra.projection.view.UserView;

public interface SpringDataUserViewRepository extends JpaRepository<UserView, UUID> {

	// 🚀 供 UserReaderAdapter 呼叫，完全對齊 API 規格
	Optional<UserView> findByTenantIdAndUsername(String tenantId, String username);

	// 🚀 供 UserProjectionProcessor 同步時使用 (因為 Event 帶有 userId)
	Optional<UserView> findByTenantIdAndId(String tenantId, UUID id);

	Optional<UserView> findByTenantId(String tenantId);
}