package com.example.forum.vo;

import java.util.List;

public record AdminPostDetailView(
        String id,
        String title,
        AdminPostSummary.AdminPostAuthor author,
        String status,
        AdminPostSummary.AdminPostCategory category,
        String content,
        String createdAt,
        String submittedAt,
        List<CommentTreeNode> comments,
        Object raw
) {
}
