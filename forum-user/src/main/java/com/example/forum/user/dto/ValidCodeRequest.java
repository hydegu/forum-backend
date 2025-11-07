package com.example.forum.user.dto;

import lombok.Data;

@Data
public class ValidCodeRequest {
    private String email;
    private String verificationCode;
}