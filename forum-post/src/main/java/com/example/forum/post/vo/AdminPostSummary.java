package com.example.forum.post.vo;

import java.util.List;

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
