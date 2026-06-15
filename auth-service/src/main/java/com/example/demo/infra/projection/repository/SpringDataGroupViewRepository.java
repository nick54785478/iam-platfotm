package com.example.demo.infra.projection.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.infra.projection.view.GroupView;

@Repository
public interface SpringDataGroupViewRepository extends JpaRepository<GroupView, UUID> {
	
	Optional<GroupView> findByTenantIdAndId(String tenantId, UUID id);

	Optional<GroupView> findByTenantIdAndGroupCode(String tenantId, String groupCode);

	List<GroupView> findByTenantId(String tenantId);
}