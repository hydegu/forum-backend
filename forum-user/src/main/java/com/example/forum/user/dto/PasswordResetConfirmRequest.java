package com.example.forum.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordResetConfirmRequest {
    @NotBlank(message = "未检测到用户名")
    private String username;

    @NotBlank(message = "未检测到用户邮箱")
    private String email;

    @NotBlank(message = "新密码不能为空")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,32}$",
            message = "密码需为8-32位，并包含字母和数字"
    )
    private String newPassword;
}