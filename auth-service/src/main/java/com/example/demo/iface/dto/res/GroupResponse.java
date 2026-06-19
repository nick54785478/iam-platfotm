package com.example.demo.iface.dto.res;

import com.example.demo.application.shared.dto.GroupRepresentation;

import java.util.List;

public class GroupResponse {

    public record GroupCreatedResource(String code, String message){}

    public record GroupRenamedResource(String code, String message){}

    public record GroupMemberAddedResource(String code, String message){}

    public record GroupMemberRemovedResource(String code, String message){}

    public record GroupRoleAssignedResource(String code, String message){}

    public record GroupRoleRevokedResource(String code, String message){}

    public record GroupViewGottenResource(String code, String message, GroupRepresentation data){}

    public record GroupsViewGottenResource(String code, String message, List<GroupRepresentation> data){}
}
