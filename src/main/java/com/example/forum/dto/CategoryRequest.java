package com.example.forum.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分类创建/更新请求体
 */
@Data
public class CategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    private String name;

}
