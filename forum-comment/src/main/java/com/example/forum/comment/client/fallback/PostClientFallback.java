package com.example.forum.comment.client.fallback;

import com.example.forum.comment.client.PostClient;
import com.example.forum.common.dto.Result;
import com.example.forum.common.enums.Code;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 帖子服务 Feign 客户端降级处理
 * 当帖子服务不可用时，返回默认值避免评论服务完全不可用
 */
@Slf4j
@Component
public class PostClientFallback implements PostClient {

    @Override
    public Result<Boolean> checkPostExists(Integer postId) {
        log.warn("帖子服务调用失败，触发降级逻辑 - checkPostExists: {}", postId);
        // 降级默认为存在，避免阻断评论创建流程
        // 实际场景可以考虑从缓存读取
        return Result.success(true);
    }

    @Override
    public Result<Integer> getPostAuthorId(Integer postId) {
        log.warn("帖子服务调用失败，触发降级逻辑 - getPostAuthorId: {}", postId);
        return Result.error(Code.SERVICE_UNAVAILABLE);
    }

    @Override
    public Result<Void> updateCommentCount(Integer postId, Integer delta) {
        log.warn("帖子服务调用失败，触发降级逻辑 - updateCommentCount: postId={}, delta={}", postId, delta);
        // 更新评论数失败不影响评论创建，返回成功但记录日志
        return Result.success(null);
    }
}