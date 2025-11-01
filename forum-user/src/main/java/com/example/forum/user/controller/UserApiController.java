package com.example.forum.user.controller;

import com.example.forum.common.dto.Result;
import com.example.forum.common.enums.Code;
import com.example.forum.user.entity.AppUser;
import com.example.forum.user.repo.UserFollowRepo;
import com.example.forum.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户服务API接口（供其他微服务调用）
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserApiController {

    private final UserService userService;
    private final UserFollowRepo userFollowRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 根据用户ID获取用户信息
     */
    @GetMapping("/id/{userId}")
    public Result<Map<String, Object>> getUserById(@PathVariable Integer userId) {
        AppUser user = userService.getById(userId);
        if (user == null) {
            return Result.error(Code.NOT_FOUND, "用户不存在");
        }
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUserName());
        userMap.put("email", user.getEmail());
        userMap.put("avatarUrl", user.getAvatarUrl());
        userMap.put("bio", user.getBio());
        userMap.put("role", user.getRole());
        return Result.success(userMap);
    }

    /**
     * 根据用户名获取用户信息
     */
    @GetMapping("/username/{username}")
    public Result<Map<String, Object>> getUserByUsername(@PathVariable String username) {
        AppUser user = userService.findByUserName(username);
        if (user == null) {
            return Result.error(Code.NOT_FOUND, "用户不存在");
        }
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUserName());
        userMap.put("email", user.getEmail());
        userMap.put("avatarUrl", user.getAvatarUrl());
        userMap.put("bio", user.getBio());
        userMap.put("role", user.getRole());
        return Result.success(userMap);
    }

    /**
     * 批量获取用户信息
     */
    @GetMapping("/batch")
    public Result<Map<Integer, Map<String, Object>>> getUsersByIds(@RequestParam("ids") String ids) {
        List<Integer> userIdList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
        
        List<AppUser> users = userService.listByIds(userIdList);
        Map<Integer, Map<String, Object>> result = users.stream()
                .collect(Collectors.toMap(
                        AppUser::getId,
                        user -> {
                            Map<String, Object> userMap = new HashMap<>();
                            userMap.put("id", user.getId());
                            userMap.put("username", user.getUserName());
                            userMap.put("email", user.getEmail());
                            userMap.put("avatarUrl", user.getAvatarUrl());
                            userMap.put("bio", user.getBio());
                            userMap.put("role", user.getRole());
                            return userMap;
                        }
                ));
        return Result.success(result);
    }

    /**
     * 检查用户是否存在
     */
    @GetMapping("/{userId}/exists")
    public Result<Boolean> checkUserExists(@PathVariable Integer userId) {
        boolean exists = userService.getById(userId) != null;
        return Result.success(exists);
    }

    /**
     * 检查用户是否关注了某个用户
     */
    @GetMapping("/{followerId}/following/{followeeId}")
    public Result<Boolean> isFollowing(@PathVariable Integer followerId,
                                      @PathVariable Integer followeeId) {
        var relation = userFollowRepo.findRelation(followerId, followeeId);
        return Result.success(relation != null);
    }

    /**
     * 批量查询关注关系，返回已关注的用户ID列表
     * 查询后会同步数据到Redis缓存
     */
    @GetMapping("/{followerId}/following/ids")
    public Result<List<Integer>> getFollowedUserIds(@PathVariable Integer followerId,
                                                    @RequestParam("followeeIds") Collection<Integer> followeeIds) {
        List<Integer> followedIds = userFollowRepo.findFolloweeIds(followerId, followeeIds);
        
        // 同步查询结果到Redis缓存
        if (!followedIds.isEmpty()) {
            try {
                String followsKey = "user:follows:" + followerId;
                String[] followeeIdsArray = followedIds.stream()
                        .map(String::valueOf)
                        .toArray(String[]::new);
                redisTemplate.opsForSet().add(followsKey, (Object[]) followeeIdsArray);
                log.debug("批量查询关注关系，已同步到Redis: followerId={}, count={}", followerId, followedIds.size());
            } catch (Exception e) {
                log.warn("同步关注关系到Redis失败: followerId={}", followerId, e);
            }
        }
        
        return Result.success(followedIds);
    }

    /**
     * 根据用户ID获取作者信息（供帖子服务使用）
     */
    @GetMapping("/{userId}/author-info")
    public Result<Map<String, Object>> getAuthorInfo(@PathVariable Integer userId) {
        AppUser user = userService.getById(userId);
        if (user == null) {
            return Result.error(Code.NOT_FOUND, "用户不存在");
        }
        Map<String, Object> author = new HashMap<>();
        author.put("id", user.getId());
        author.put("name", user.getUserName());
        author.put("avatar", user.getAvatarUrl());
        author.put("bio", user.getBio());
        return Result.success(author);
    }
}
