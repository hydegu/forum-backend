package com.example.forum.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PasswordResetRequest {

    @NotBlank(message = "账号标识不能为空")
    private String identifier;
}
