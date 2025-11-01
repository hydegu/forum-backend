package com.example.forum.user.vo;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String refreshToken;
    private String role;
    private long expiresIn;
    private long refreshExpiresIn;

    public LoginResponse(String token, String refreshToken, String role, long expiresIn, long refreshExpiresIn) {
        this.token = token;
        this.refreshToken = refreshToken;
        this.role = role;
        this.expiresIn = expiresIn;
        this.refreshExpiresIn = refreshExpiresIn;
    }
}