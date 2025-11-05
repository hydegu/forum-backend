package com.example.forum.comment.client;

import com.example.forum.comment.client.fallback.UserClientFallback;
import com.example.forum.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 用户服务Feign客户端
 * 用于评论服务调用用户服务接口
 */
@FeignClient(name = "forum-user-service", path = "/api/users", fallback = UserClientFallback.class)
public interface UserClient {

    /**
     * 检查用户是否存在
     */
    @GetMapping("/{userId}/exists")
    Result<Boolean> checkUserExists(@PathVariable("userId") Integer userId);
}
