package com.example.demo.infra.persistence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.persistence.entity.UserProfileEntity;


@Repository
public interface UserProfileRepository extends JpaRepository<UserProfileEntity, String> {

	// 核心防禦：依據多租戶 + 物理主鍵隔離查詢
	Optional<UserProfileEntity> findByTenantIdAndId(String tenantId, String id);
}