package com.example.demo.application.port;

import java.util.Optional;

import com.example.demo.application.domain.user.aggregate.User;
import com.example.demo.application.domain.user.aggregate.vo.UserId;

public interface UserWriterPort {
	
	Optional<User> findById(UserId id);

	Optional<User> findByUsername(String username);

	void save(User user);
}