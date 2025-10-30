package com.example.forum.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateUserProfileRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 32, message = "用户名长度需在2-32个字符之间")
    private String username;

    @Email(message = "邮箱格式错误")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;

    @Pattern(regexp = "^(|\\d{11})$", message = "手机号格式错误")
    private String phone;

    @Size(max = 200, message = "简介长度不能超过200个字符")
    private String bio;

    @Size(max = 512, message = "头像链接长度不能超过512个字符")
    private String avatarUrl;
}
