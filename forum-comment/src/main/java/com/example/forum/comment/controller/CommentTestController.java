package com.example.forum.comment.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 评论服务测试控制器
 *
 * @author Forum Team
 */
@RestController
@RequestMapping("/api/comment")
public class CommentTestController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "forum-comment-service");
        result.put("status", "UP");
        result.put("port", 8083);
        result.put("message", "评论微服务运行正常");
        return result;
    }
}