package com.example.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户关注关系。
 */
@Data
@Accessors(chain = true)
@TableName("user_follows")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("follower_id")
    private Integer followerId;

    @TableField("followee_id")
    private Integer followeeId;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
