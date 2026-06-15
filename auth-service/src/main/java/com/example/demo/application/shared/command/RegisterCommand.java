package com.example.demo.application.shared.command;

/**
 * 註冊專用自包含命令 Record
 */
public record RegisterCommand(String tenantCode, String username, String rawPassword, String email) {
}