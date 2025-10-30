package com.example.forum.vo;

import java.time.LocalDateTime;

/**
 * 分类接口响应对象
 */
public record CategoryResponse(
        Integer id,
        String name,
        Long postCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
