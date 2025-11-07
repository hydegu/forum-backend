package com.example.forum.post.vo;

import com.example.forum.post.entity.Author;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailView {
    private Integer id;
    private String title;
    private String subtitle;
    private String content;
    private List<String> images;
    private Author author;
    private CategoryResponse category;
    private Integer likeCount;
    private Integer likes;
    private Boolean liked;
    private Integer commentCount;
    private Integer viewCount;
    private Boolean followed;
    private Boolean pinned;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
