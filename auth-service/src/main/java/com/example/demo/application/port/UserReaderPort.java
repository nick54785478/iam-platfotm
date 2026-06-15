package com.example.demo.application.port;

import java.util.List;
import java.util.Optional;

import com.example.demo.application.shared.dto.UserRepresentation;

public interface UserReaderPort {

	// 核心調整：改以 username 作為租戶內唯一查詢的主角
	Optional<UserRepresentation> fetchByUsername(String tenantId, String username);

    List<UserRepresentation> fetchAllByTenant(String tenantId);
}