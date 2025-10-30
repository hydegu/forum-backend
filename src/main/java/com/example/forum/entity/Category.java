package com.example.forum.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@TableName("post_categories")
@NoArgsConstructor
public class Category {
    @TableId( value=" id" ,type = IdType.AUTO)
    private Integer id;
    @TableField("name")
    private String name;
    @TableField("post_count")
    private long postCount;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Category(Integer id, String name) {
        this.id = id;
        this.name = name;
    }
}
