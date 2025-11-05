package com.example.forum.post.client.fallback;

import com.example.forum.common.dto.Result;
import com.example.forum.common.enums.Code;
import com.example.forum.post.client.UserClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端降级处理
 * 当用户服务不可用时，返回默认值避免服务雪崩
 */
@Slf4j
@Component
public class UserClientFallback implements UserClient {

    @Override
    public Result<Map<String, Object>> getUserById(Integer userId) {
        log.warn("用户服务调用失败，触发降级逻辑 - getUserById: {}", userId);
        return Result.error(Code.SERVICE_UNAVAILABLE);
    }

    @Override
    public Result<Map<String, Object>> getUserByUsername(String username) {
        log.warn("用户服务调用失败，触发降级逻辑 - getUserByUsername: {}", username);
        return Result.error(Code.SERVICE_UNAVAILABLE);
    }

    @Override
    public Result<Map<Integer, Map<String, Object>>> getUsersByIds(String ids) {
        log.warn("用户服务调用失败，触发降级逻辑 - getUsersByIds: {}", ids);
        // 返回空 Map，避免 NPE
        return Result.success(Collections.emptyMap());
    }

    @Override
    public Result<Boolean> isFollowing(Integer followerId, Integer followeeId) {
        log.warn("用户服务调用失败，触发降级逻辑 - isFollowing: {} -> {}", followerId, followeeId);
        // 降级默认为未关注，不影响核心功能
        return Result.success(false);
    }

    @Override
    public Result<Boolean> checkUserExists(Integer userId) {
        log.warn("用户服务调用失败，触发降级逻辑 - checkUserExists: {}", userId);
        // 降级默认为存在，避免阻断业务流程
        return Result.success(true);
    }

    @Override
    public Result<List<Integer>> getFollowedUserIds(Integer followerId, Collection<Integer> followeeIds) {
        log.warn("用户服务调用失败，触发降级逻辑 - getFollowedUserIds: followerId={}", followerId);
        // 返回空列表，表示没有关注任何人
        return Result.success(Collections.emptyList());
    }
}