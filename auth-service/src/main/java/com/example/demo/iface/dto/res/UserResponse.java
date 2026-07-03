package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.UserPermissionContextRepresentation;
import com.example.demo.application.shared.dto.UserRepresentation;

import java.util.List;

public class UserResponse {

    public record UserCreatedResource(String code, String message){}

    public record PasswordChangedResource(String code, String message){}

    public record UserProfileUpdatedResource(String code, String message){}

    public record UserDeletedResource(String code, String message){}

    public record UserReactivatedResource(String code, String message){}

    public record UserViewGottenResource(String code, String message, UserRepresentation data){}

    public record UsersViewGottenResource(String code, String message, List<UserRepresentation> data){}

    public record UserPermissionContextGottenResource(String code, String message, UserPermissionContextRepresentation data){}

}
