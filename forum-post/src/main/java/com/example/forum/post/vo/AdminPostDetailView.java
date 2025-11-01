package com.example.forum.post.vo;

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
        List<Object> comments,  // 评论数据由评论服务提供，这里使用Object类型
        Object raw
) {
}
