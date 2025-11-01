package com.example.forum.comment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PostCommentCreateRequest {
    @NotBlank(message = "评论内容不能为空")
    private String content;
    private Integer parentId;
}
