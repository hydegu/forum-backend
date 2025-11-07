package com.example.forum.post.client;

import com.example.forum.common.dto.Result;
import com.example.forum.post.client.fallback.CommentClientFallback;
import com.example.forum.post.vo.AdminPostDetailView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 评论服务Feign客户端
 * 用于帖子服务调用评论服务获取评论列表
 */
@FeignClient(name = "forum-comment-service", path = "/api/comments", fallback = CommentClientFallback.class)
public interface CommentClient {

    /**
     * 获取帖子的评论列表
     * @param postId 帖子ID
     * @param page 页码
     * @param size 每页大小
     * @return 评论列表
     */
    @GetMapping("/posts/{postId}/for-post-service")
    Result<com.example.forum.common.vo.PageResponse<Object>> getComments(@PathVariable("postId") Integer postId,
                                                                          @RequestParam("page") Integer page,
                                                                          @RequestParam("size") Integer size);
}
