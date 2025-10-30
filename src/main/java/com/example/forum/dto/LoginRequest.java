package com.example.forum.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;  // 用户名
    private String password;  // 密码
}

