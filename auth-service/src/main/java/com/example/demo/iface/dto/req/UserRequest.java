package com.example.demo.iface.dto.req;

public class UserRequest {

    /**
     * 用戶建立請求結構體
     */
    public record CreateUserResource(String username, String password, String email) {
    }

    /**
     * 密碼變更請求結構體
     */
    public record ChangePasswordRequest(String newPassword) {
    }


    /**
     * 用戶基礎資料變更請求結構體
     */
    public record UpdateUserProfileRequest(String email) {
    }

}
