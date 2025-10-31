package com.example.forum.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "com.jwt")
public class JwtProperties {

    /**
     * 用户端访问令牌配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;

    /**
     * 刷新令牌配置
     */
    private String refreshSecretKey;
    private long refreshTtl;
    private String refreshTokenName;
}
