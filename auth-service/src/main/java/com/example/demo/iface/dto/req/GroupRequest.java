package com.example.demo.iface.dto.req;

public class GroupRequest {

    /**
     * 群組建立請求結構體
     * */
    public record CreateGroupResource(String groupName, String groupCode) {
    }

    /**
     * 群組更名請求結構體
     * */
    public record RenameGroupResource(String newName) {
    }
}
