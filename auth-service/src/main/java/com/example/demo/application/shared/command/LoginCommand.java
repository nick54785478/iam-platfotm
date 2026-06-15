package com.example.demo.application.shared.command;


/**
 * 🚀 頂規完備版：LoginCommand 顯式攜帶租戶標籤，達成完全自包含
 */
public record LoginCommand(String tenantCode, String username, String rawPassword) {
	public LoginCommand{if(tenantCode==null||tenantCode.isBlank()){throw new IllegalArgumentException("Tenant code cannot be empty for login intent");}if(username==null||username.isBlank()){throw new IllegalArgumentException("Username cannot be empty");}if(rawPassword==null||rawPassword.isBlank()){throw new IllegalArgumentException("Password cannot be empty");}}
}