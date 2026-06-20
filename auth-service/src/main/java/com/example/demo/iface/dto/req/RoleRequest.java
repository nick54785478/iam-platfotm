package com.example.demo.iface.dto.req;

public class RoleRequest {

    /**
     * 角色建立請求結構體
     * */
    public record CreateRoleRequest(String roleName, String roleCode) {
    }

    /**
     * 角色更名請求結構體
     * */
    public record RenameRoleRequest(String newName) {
    }

    /**
     * 跨服務權限上報請求結構體
     * */
    public record AssignPermissionRequest(String systemCode, String permissionCode, String permissionName) {
    }

}
