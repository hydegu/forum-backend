package com.example.forum.post.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostListResponse {
    private List<PostSummaryView> records;
    private Long total;
    private Map<String, Object> extra;
    private Long pages;
    private Boolean end;
}
