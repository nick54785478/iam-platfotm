package com.example.demo.iface.dto.req;

public class AuthRequest {

    public record LoginResource(String tenantCode, String username, String password) {
    }

    public record RegisterResource(String tenantCode, String username, String password, String email) {
    }
}