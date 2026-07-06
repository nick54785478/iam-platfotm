package com.example.demo.infra.projection.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.projection.view.KycBackofficeView;

@Repository
public interface KycBackofficeViewRepository extends JpaRepository<KycBackofficeView, String> {

	Optional<KycBackofficeView> findByTenantIdAndId(String tenantId, String id);

	// 供後台 UI 呼叫：依據租戶與審核狀態撈取分頁列表
	Page<KycBackofficeView> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);
}