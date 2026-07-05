package com.example.demo.infra.projection.repository;


import com.example.demo.infra.projection.view.UserProfileView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileViewRepository extends JpaRepository<UserProfileView, String> {

    // 依據租戶與 ID 尋找視圖快照
    Optional<UserProfileView> findByTenantIdAndId(String tenantId, String id);
}