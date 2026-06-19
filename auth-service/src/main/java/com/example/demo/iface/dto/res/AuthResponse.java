package com.example.demo.iface.dto.res;

public record AuthResponse() {

    public record JwtTokenGeneratedResource(String code, String messaget, String token) {}

    public record RegisteredResource(String code, String message){}
}
