package com.example.forum.user.dto;

import lombok.Data;

@Data
public class TokenRefreshRequest {
    private String refreshToken;
}