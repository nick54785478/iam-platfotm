package com.example.demo.infra.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.entity.UserIdentityEntity;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, String> {

	// 核心防禦：依據多租戶 + 物理主鍵隔離查詢
	Optional<UserIdentityEntity> findByTenantIdAndId(String tenantId, String id);
}