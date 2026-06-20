package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.RoleRepresentation;

import java.util.List;

public class RoleResponse {

    public record RoleCreatedResource(String code, String message){}

    public record RoleRenamedResource(String code, String message){}

    public record PermissionAssignedResource(String code, String message){}

    public record RoleViewGottenResource(String code, String message, RoleRepresentation data){}

    public record RolesViewGottenResource(String code, String message, List<RoleRepresentation> data){}

    public record UserRoleAssignedResource(String code, String message){}


}
