package com.example.forum.user.vo;

import lombok.Data;

/**
 * 登录响应VO
 * 安全优化：
 * 1. 移除refreshToken字段 - refreshToken仅通过HttpOnly Cookie传输，防止XSS攻击
 * 2. 移除role字段 - role信息已在JWT中加密存储，前端可通过/api/auth/me获取
 */
@Data
public class LoginResponse {
    private String token;
    private long expiresIn;
    private long refreshExpiresIn;

    public LoginResponse(String token, long expiresIn, long refreshExpiresIn) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
    }
}