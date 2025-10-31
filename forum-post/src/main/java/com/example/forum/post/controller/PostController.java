package com.example.forum.post.controller;

import com.example.forum.post.dto.PostCreateRequest;
import com.example.forum.post.dto.PostRequest;
import com.example.forum.post.service.CategoryService;
import com.example.forum.post.service.PostService;
import com.example.forum.post.service.PostLikeService;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.CategoryResponse;
import com.example.forum.post.vo.PostDetailView;
import com.example.forum.post.vo.PostListResponse;
import com.example.forum.post.vo.PostSummaryView;
import com.example.forum.post.vo.TrendingPostView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;

    @GetMapping("/posts")
    public PostListResponse getApprovedPosts(@Valid PostRequest postRequest) {
        Integer current = Optional.ofNullable(postRequest.getPage()).orElse(1);
        Integer size = Optional.ofNullable(postRequest.getSize()).orElse(10);
        Integer currentUserId = resolveCurrentUserId();
        return postService.pageWithSummary(current, size, null,
                postRequest.getStatus(), postRequest.getQ(), postRequest.getCategoryId(), currentUserId);
    }

    @GetMapping("/posts/trending")
    public List<TrendingPostView> getTrendingPosts(@RequestParam(required = false) Integer limit) {
        int size = Optional.ofNullable(limit).filter(l -> l > 0).orElse(5);
        return postService.getTrendingPostViews(size);
    }

    @GetMapping("/posts/{postId}")
    public PostDetailView getPostDetail(@PathVariable Integer postId) {
        Integer userId = resolveCurrentUserId();
        return postService.getPostDetail(postId, userId);
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Map<String, Object>> likePost(@PathVariable Integer postId) {
        Integer currentUserId = requireCurrentUserId();
        postService.likePost(postId, currentUserId);
        return ResponseEntity.ok(Map.of("liked", true));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Map<String, Object>> unlikePost(@PathVariable Integer postId) {
        Integer currentUserId = requireCurrentUserId();
        postService.unlikePost(postId, currentUserId);
        return ResponseEntity.ok(Map.of("liked", false));
    }

    @PostMapping("/posts")
    public ResponseEntity<PostDetailView> createPost(@Valid @RequestBody PostCreateRequest request) {
        Integer currentUserId = requireCurrentUserId();
        postService.createPost(request, currentUserId);
        PostDetailView detail = postService.getPostDetail(null, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @GetMapping("/users/me/posts")
    public PageResponse<PostSummaryView> pageMyPosts(@RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @RequestParam(required = false) String status) {
        Integer currentUserId = requireCurrentUserId();
        return postService.pageMyPosts(currentUserId, status, page, size);
    }

    private Integer resolveCurrentUserId() {
        // TODO: 从JWT token中解析用户ID
        return null;
    }

    private Integer requireCurrentUserId() {
        Integer userId = resolveCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("Unauthorized");
        }
        return userId;
    }
}
