package com.example.forum.post.dto;

import lombok.Data;

@Data
public class PostRequest {
    private String status = "approved";
    private Integer page = 1;
    private Integer size = 10;
    private String sort = "hot";
    private String q;
    private Integer categoryId;
}
