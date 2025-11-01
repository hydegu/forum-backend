package com.example.forum.comment.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Author {
    private Integer id;
    private String username;
    private String avatar;
    private String bio;
}
