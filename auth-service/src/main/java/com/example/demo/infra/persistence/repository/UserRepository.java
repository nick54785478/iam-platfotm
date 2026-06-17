package com.example.demo.infra.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.entity.user.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {

	// 多租戶安全查詢：根據租戶 ID 與用戶 ID 查詢
	Optional<UserEntity> findByTenantIdAndId(String tenantId, UUID id);

	// 多租戶安全查詢：根據租戶 ID 與用戶名查詢（用於登入）
	Optional<UserEntity> findByTenantIdAndUsername(String tenantId, String username);
}