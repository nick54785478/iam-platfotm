package com.example.demo.infra.adapter;


import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.demo.application.port.PasswordEncoderPort;

@Component
class BCryptPasswordEncoderAdapter implements PasswordEncoderPort {

    // 使用 Spring Security 經典的 BCrypt 強固加密演算法
    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return delegate.matches(rawPassword, encodedPassword);
    }
}