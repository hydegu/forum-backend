package com.example.forum.post.vo;

import java.time.LocalDateTime;

public record CategoryResponse(
        Integer id,
        String name,
        Long postCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
