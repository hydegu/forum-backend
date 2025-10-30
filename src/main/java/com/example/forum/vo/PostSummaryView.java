package com.example.forum.vo;

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
public class PostSummaryView {

    private String id;

    private String title;
    private String subtitle;
    private String summary;
    private String contentPreview;

    private Boolean pinned;

    private List<String> images;
    private String thumbnail;

    private String authorId;
    private String authorName;

    private String categoryId;
    private String categoryName;

    private LocalDateTime createdAt;

    private Integer commentCount;
    private Integer viewCount;
    private Integer likeCount;
    private Boolean liked;
    private Boolean following;
}
