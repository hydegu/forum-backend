package com.example.forum.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户服务测试控制器
 *
 * @author Forum Team
 */
@RestController
@RequestMapping("/api/user")
public class UserTestController {

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "forum-user-service");
        result.put("status", "UP");
        result.put("port", 8081);
        result.put("message", "用户微服务运行正常");
        return result;
    }
}