package com.example.forum.vo;

/**
 * 后台用户列表视图
 */
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
