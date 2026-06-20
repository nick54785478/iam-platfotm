package com.example.demo.iface.rest;

import com.example.demo.iface.dto.res.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.application.service.UserPermissionQueryService;
import com.example.demo.application.shared.dto.UserPermissionContextRepresentation;

@RestController
@RequestMapping("/api/users")
public class UserPermissionController {

    private final UserPermissionQueryService userPermissionQueryService;

    public UserPermissionController(UserPermissionQueryService userPermissionQueryService) {
        this.userPermissionQueryService = userPermissionQueryService;
    }

    /**
     * <b>頂規鑑權接口：查出指定同仁在當前租戶空間內的所有角色與權限大上下文</b>
     * <p>
     * <b>HTTP 語意：</b> {@code GET /api/users/{username}/permissions-context}
     * </p>
     * <p>
     * <b>防禦地雷：</b> 一樣加上 {@code :.+} 正則匹配，無痛吞下諸如 {@code V-NICK.GH.ZHANG} 等特殊用戶名。
     * </p>
     */
    @GetMapping("/{username:.+}/permissions-context")
    public ResponseEntity<UserResponse.UserPermissionContextGottenResource> getUserPermissionsContext(
            @PathVariable String username) {
        // 經由攔截器隱式防禦 tenantId，一發記憶體內極速組裝回傳
        UserPermissionContextRepresentation context = userPermissionQueryService.getUserPermissionContext(username);
        return ResponseEntity.ok(new UserResponse.UserPermissionContextGottenResource("200", "Success", context));
    }
}