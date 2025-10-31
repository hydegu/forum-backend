package com.example.forum.post.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 帖子服务测试控制器
 *
 * @author Forum Team
 */
@RestController
@RequestMapping("/api/post")
public class PostTestController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "forum-post-service");
        result.put("status", "UP");
        result.put("port", 8082);
        result.put("message", "帖子微服务运行正常");
        return result;
    }
}