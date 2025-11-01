package com.example.forum.comment.vo;

import com.example.forum.comment.entity.Author;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentTreeNode {
    private Integer id;
    private Integer parentId;
    private Integer rootId;
    private String content;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Author author;
    @Builder.Default
    private List<CommentTreeNode> replies = new ArrayList<>();
}
