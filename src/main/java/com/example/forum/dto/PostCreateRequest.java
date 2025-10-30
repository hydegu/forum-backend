package com.example.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 创建/保存帖子请求体。
 */
@Data
public class PostCreateRequest {

    @NotBlank(message = "帖子标题不能为空")
    private String title;

    private String subtitle;

    @NotBlank(message = "帖子内容不能为空")
    private String content;

    @NotNull(message = "分类不能为空")
    private Integer categoryId;

    private List<String> images;

    /**
     * 草稿/待审核/已审核等状态，默认待审核。
     */
    private String status;

    private Boolean pinned;
}
