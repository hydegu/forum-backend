package com.example.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 帖子点赞记录。
 */
@Data
@Accessors(chain = true)
@TableName("post_likes")
public class PostLike {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("post_id")
    private Integer postId;

    @TableField("user_id")
    private Integer userId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
