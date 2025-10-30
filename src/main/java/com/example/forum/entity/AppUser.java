package com.example.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@TableName("users")
@Accessors(chain = true)
public class AppUser {
    @TableId(type = IdType.AUTO)
    private Integer id;
    @TableField("username")
    private String userName;
    private String email;
    @TableField("avatar_url")
    private String avatarUrl;
    @TableField("bio")
    private String bio;
    private String password;
    private String phone;
    private String status;
    private String role;
    @TableField("created_at")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDateTime createdAt;
    @TableField("updated_at")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDateTime updatedAt;
}
