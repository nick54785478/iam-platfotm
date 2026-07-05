package com.example.demo.application.domain.shared.vo;


import java.net.MalformedURLException;
import java.net.URL;

/**
 * <b>[值物件] 社交連結</b>
 * <p>封裝單一外部連結的平台與網址，並進行基本的格式防禦。</p>
 */
public record SocialLink(SocialPlatform platform, String url) {
    public SocialLink {
        if (platform == null) {
            throw new IllegalArgumentException("Social platform cannot be null");
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        try {
            new URL(url); // 簡易防禦，確保是合法的 URL 格式
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid URL format: " + url);
        }
    }
}