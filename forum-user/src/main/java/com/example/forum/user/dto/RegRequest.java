package com.example.forum.user.dto;

import lombok.Data;

@Data
public class RegRequest {
    private String username;
    private String password;
    private String email;
    private String mobileNumber;
    private String verificationCode;
}