package com.example.forum.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增评论/回复请求体。
 */
@Data
public class PostCommentCreateRequest {

    @NotBlank(message = "评论内容不能为空")
    private String content;

    /**
     * 父级评论ID，可为空表示一级评论。
     */
    private Integer parentId;
}
