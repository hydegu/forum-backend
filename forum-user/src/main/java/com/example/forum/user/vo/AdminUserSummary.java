package com.example.forum.user.vo;

public record AdminUserSummary(
        String id,
        String username,
        String email,
        String status,
        String registeredAt,
        String bio,
        String avatar
) {
}