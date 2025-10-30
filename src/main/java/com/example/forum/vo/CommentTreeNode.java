package com.example.forum.vo;

import com.example.forum.entity.Author;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论树节点视图。
 */
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
