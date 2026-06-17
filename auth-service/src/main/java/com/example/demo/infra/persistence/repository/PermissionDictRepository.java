package com.example.demo.infra.persistence.repository;

import com.example.demo.infra.projection.view.PermissionDictView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionDictRepository extends JpaRepository<PermissionDictView, Long> {

    Optional<PermissionDictView> findByTenantIdAndCode(String tenantId, String code);
}
