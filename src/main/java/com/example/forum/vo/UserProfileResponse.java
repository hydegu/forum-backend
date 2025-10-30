package com.example.forum.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {
    private String id;
    private String userName;
    private String avatarUrl;
    private String role;
    private String email;
    private String bio;
    private String phone;
    private String updatedAt;
}
