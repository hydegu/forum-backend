package com.example.forum.vo;

import lombok.Data;

@Data
public class LoginResponse {

    /**
     * 访问令牌（Access Token）
     */
    private String token;

    /**
     * 刷新令牌（Refresh Token）
     */
    private String refreshToken;

    /**
     * 用户角色信息
     */
    private String role;

    /**
     * 访问令牌有效期（秒）
     */
    private long expiresIn;

    /**
     * 刷新令牌有效期（秒）
     */
    private long refreshExpiresIn;

    public LoginResponse(String token, String refreshToken, String role, long expiresIn, long refreshExpiresIn) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.role = role;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
    }
}
