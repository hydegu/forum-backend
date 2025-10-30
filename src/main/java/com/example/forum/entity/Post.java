package com.example.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@TableName(value = "posts", autoResultMap = true)
@Accessors(chain = true)
@Data
public class Post {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("title")
    private String title;

    @TableField("subtitle")
    private String subtitle;

    @TableField("content")
    private String content;

    @TableField("author_id")
    private Integer authorId;

    @TableField("status")
    private String status;

    @TableField("heat")
    private Integer heat;

    @TableField(value = "images", typeHandler = JacksonTypeHandler.class)
    private List<String> images;

    @TableField("created_at")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDateTime createdAt;

    @TableField("updated_at")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDateTime updatedAt;

    @TableField("view_count")
    private Integer viewCount;

    @TableField("like_count")
    private Integer likeCount;

    @TableField("comment_count")
    private Integer commentCount;

    @TableField("pinned")
    private Boolean pinned;

    @TableField("category_id")
    private Integer categoryId;

    @TableField(exist = false)
    private String authorName;

    @TableField(exist = false)
    private String authorAvatar;

    @TableField(exist = false)
    private String authorBio;

    @TableField(exist = false)
    private String categoryName;
}
