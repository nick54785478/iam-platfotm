package com.example.demo.application.shared.dto;

import java.util.Set;

public record UserRepresentation(String id, String username, String email, String status, Set<String> roles) {
}