package com.example.forum.vo;

import java.util.List;

/**
 * 后台帖子管理视图
 */
public record AdminPostSummary(
        String id,
        String title,
        AdminPostAuthor author,
        String status,
        AdminPostCategory category,
        String submittedAt,
        List<Object> comments,
        Object raw
) {

    public record AdminPostAuthor(
            String id,
            String username,
            String avatar
    ) {
    }

    public record AdminPostCategory(
            String id,
            String name
    ) {
    }
}
