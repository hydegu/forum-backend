package com.example.forum.post.controller;

import com.example.forum.common.dto.Result;
import com.example.forum.common.enums.Code;
import com.example.forum.post.entity.Post;
import com.example.forum.post.repo.PostRepo;
import com.example.forum.post.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子服务API接口（供其他微服务调用，如评论服务）
 */
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Slf4j
public class PostApiController {

    private final PostService postService;
    private final PostRepo postRepo;

    /**
     * 检查帖子是否存在
     */
    @GetMapping("/{postId}/exists")
    public Result<Boolean> checkPostExists(@PathVariable Integer postId) {
        Post post = postService.getById(postId);
        return Result.success(post != null);
    }

    /**
     * 获取帖子作者ID
     */
    @GetMapping("/{postId}/author-id")
    public Result<Integer> getPostAuthorId(@PathVariable Integer postId) {
        Post post = postService.getById(postId);
        if (post == null) {
            return Result.error(Code.NOT_FOUND, "帖子不存在");
        }
        return Result.success(post.getAuthorId());
    }

    /**
     * 更新帖子评论数
     */
    @PutMapping("/{postId}/comment-count")
    public Result<Void> updateCommentCount(@PathVariable Integer postId,
                                          @RequestParam("delta") Integer delta) {
        int affected = postRepo.updateCommentCount(postId, delta);
        if (affected > 0) {
            return Result.success(null);
        } else {
            return Result.error(Code.NOT_FOUND, "帖子不存在或更新失败");
        }
    }
}
