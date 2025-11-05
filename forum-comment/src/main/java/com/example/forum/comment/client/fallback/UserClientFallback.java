package com.example.forum.comment.client.fallback;

import com.example.forum.comment.client.UserClient;
import com.example.forum.common.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户服务 Feign 客户端降级处理
 * 当用户服务不可用时，返回默认值避免评论服务完全不可用
 */
@Slf4j
@Component
public class UserClientFallback implements UserClient {

    @Override
    public Result<Boolean> checkUserExists(Integer userId) {
        log.warn("用户服务调用失败，触发降级逻辑 - checkUserExists: {}", userId);
        // 降级默认为存在，避免阻断评论创建流程
        return Result.success(true);
    }
}