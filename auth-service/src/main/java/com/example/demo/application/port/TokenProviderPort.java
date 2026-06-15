package com.example.demo.application.port;

import java.util.Set;

public interface TokenProviderPort {

	String createToken(String username, Set<String> permissionStrings);

	String extractUsername(String token);
}