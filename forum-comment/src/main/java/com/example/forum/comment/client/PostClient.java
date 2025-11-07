package com.example.forum.comment.client;

import com.example.forum.comment.client.fallback.PostClientFallback;
import com.example.forum.common.dto.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 帖子服务Feign客户端
 * 用于评论服务调用帖子服务接口
 */
@FeignClient(name = "forum-post-service", path = "/api/posts", fallback = PostClientFallback.class)
public interface PostClient {

    /**
     * 检查帖子是否存在
     */
    @GetMapping("/{postId}/exists")
    Result<Boolean> checkPostExists(@PathVariable("postId") Integer postId);

    /**
     * 获取帖子作者ID
     */
    @GetMapping("/{postId}/author-id")
    Result<Integer> getPostAuthorId(@PathVariable("postId") Integer postId);

    /**
     * 更新帖子评论数
     */
    @PutMapping("/{postId}/comment-count")
    Result<Void> updateCommentCount(@PathVariable("postId") Integer postId,
                                    @RequestParam("delta") Integer delta);
}
