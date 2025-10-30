package com.example.forum.dto;

import lombok.Data;

@Data
public class TokenRefreshRequest {

    /**
     * 客户端持有的刷新令牌
     */
    private String refreshToken;
}
