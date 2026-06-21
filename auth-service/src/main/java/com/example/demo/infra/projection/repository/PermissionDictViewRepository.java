package com.example.demo.infra.projection.repository;

import com.example.demo.infra.projection.view.PermissionDictView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PermissionDictViewRepository extends JpaRepository<PermissionDictView, String> {

    Optional<PermissionDictView> findByTenantIdAndCode(String tenantId, String code);
}
