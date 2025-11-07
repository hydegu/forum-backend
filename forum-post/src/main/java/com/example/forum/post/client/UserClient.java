package com.example.forum.post.client;

import com.example.forum.common.dto.Result;
import com.example.forum.post.client.fallback.UserClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 用户服务Feign客户端
 * 用于帖子服务调用用户服务获取用户信息
 */
@FeignClient(name = "forum-user-service", path = "/api", fallback = UserClientFallback.class)
public interface UserClient {

    /**
     * 根据用户ID获取用户信息（简化版，返回Map）
     */
    @GetMapping("/users/id/{userId}")
    Result<Map<String, Object>> getUserById(@PathVariable("userId") Integer userId);

    /**
     * 根据用户名获取用户信息
     */
    @GetMapping("/users/username/{username}")
    Result<Map<String, Object>> getUserByUsername(@PathVariable("username") String username);

    /**
     * 批量获取用户信息
     */
    @GetMapping("/users/batch")
    Result<Map<Integer, Map<String, Object>>> getUsersByIds(@RequestParam("ids") String ids);
    
    /**
     * 检查用户是否关注了某个用户
     */
    @GetMapping("/users/{followerId}/following/{followeeId}")
    Result<Boolean> isFollowing(@PathVariable("followerId") Integer followerId,
                                @PathVariable("followeeId") Integer followeeId);

    /**
     * 检查用户是否存在
     */
    @GetMapping("/users/{userId}/exists")
    Result<Boolean> checkUserExists(@PathVariable("userId") Integer userId);

    /**
     * 批量查询关注关系，返回已关注的用户ID列表
     */
    @GetMapping("/users/{followerId}/following/ids")
    Result<List<Integer>> getFollowedUserIds(@PathVariable("followerId") Integer followerId,
                                             @RequestParam("followeeIds") Collection<Integer> followeeIds);
}
