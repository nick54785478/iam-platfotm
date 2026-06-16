package com.example.demo.application.domain.permission.repository;


import com.example.demo.application.domain.permission.aggregate.PermissionDefinition;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionCode;
import com.example.demo.application.domain.permission.aggregate.vo.PermissionId;
import com.example.demo.application.domain.shared.vo.TenantId;

import java.util.Optional;

/**
 * <h2>[領域層/寫入端] 權限定義倉儲合約 (Secondary Port)</h2>
 * <p>
 * <b>【架構定位】</b>：<br>
 * 定義權限聚合根的持久化驅動合約。嚴格遵守六角架構原則，全面採用強型別值物件 (Value Object) 作為參數，
 * 徹底消滅基本型別偏執 (Primitive Obsession)，從源頭防堵越權與參數錯位。
 * </p>
 */
public interface PermissionDefinitionRepository {

    /**
     * 透過租戶 ID 與權限 ID 尋找權限 (具備租戶隔離防護)
     */
    Optional<PermissionDefinition> findById(TenantId tenantId, PermissionId id);

    /**
     * 透過租戶 ID 與權限代碼精準尋找權限
     * <p>💡 <b>領域知識：</b> 在同一個租戶下，PermissionCode 具備絕對唯一性約束。</p>
     *
     * @param tenantId 租戶識別值物件
     * @param code     權限代碼值物件
     */
    Optional<PermissionDefinition> findByTenantIdAndCode(TenantId tenantId, PermissionCode code);

    /**
     * 檢查特定租戶下該權限代碼是否已存在
     * <p>用於新建權限前的等冪性校驗與防呆檢核。</p>
     */
    boolean existsByTenantIdAndCode(TenantId tenantId, PermissionCode code);

    /**
     * 儲存權限聚合根的最新狀態。
     * <p>⚠️ <b>事件驅動核心：</b> 此方法執行時，底層應負責連帶將聚合根內累積的 DomainEvent 派發至 Spring Event Bus。</p>
     */
    PermissionDefinition save(PermissionDefinition permissionDefinition);
}