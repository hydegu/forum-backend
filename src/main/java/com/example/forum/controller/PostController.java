package com.example.forum.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.forum.dto.PostCommentCreateRequest;
import com.example.forum.dto.PostCreateRequest;
import com.example.forum.dto.PostRequest;
import com.example.forum.dto.Result;
import com.example.forum.entity.AppUser;
import com.example.forum.entity.Post;
import com.example.forum.entity.PostComment;
import com.example.forum.service.CommentService;
import com.example.forum.service.PostService;
import com.example.forum.service.UserService;
import com.example.forum.utils.SecurityUtils;
import com.example.forum.vo.CommentTreeNode;
import com.example.forum.vo.PageResponse;
import com.example.forum.vo.PostDetailView;
import com.example.forum.vo.PostListResponse;
import com.example.forum.vo.PostSummaryView;
import com.example.forum.vo.TrendingPostView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final UserService userService;

    @GetMapping("/posts")
    public PostListResponse getApprovedPosts(@Valid PostRequest postRequest) {
        Integer current = Optional.ofNullable(postRequest.getPage()).orElse(1);
        Integer size = Optional.ofNullable(postRequest.getSize()).orElse(10);

        LambdaQueryWrapper<Post> wrapper = Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, "approved");
        String q = postRequest.getQ();
        Integer categoryId = postRequest.getCategoryId();
        String sort = postRequest.getSort();
        if (StringUtils.hasText(q)) {
            wrapper.and(w -> w.like(Post::getTitle, q)
                    .or()
                    .like(Post::getAuthorId, q)
                    .or()
                    .like(Post::getContent, q)
                    .or()
                    .like(Post::getAuthorName, q));
        }
        wrapper.eq(categoryId != null, Post::getCategoryId, categoryId);
        if ("new".equalsIgnoreCase(sort)) {
            wrapper.orderByDesc(Post::getCreatedAt);
        } else {
            wrapper.orderByDesc(Post::getHeat)
                    .orderByDesc(Post::getCreatedAt);
        }
        Integer currentUserId = resolveCurrentUserId();
        return postService.pageWithSummary(current, size, wrapper,
                postRequest.getStatus(), q, postRequest.getCategoryId(), currentUserId);
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

    @GetMapping("/posts/{postId}/comments")
    public PageResponse<CommentTreeNode> pageComments(@PathVariable Integer postId,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer size) {
        return commentService.pageComments(postId, page, size);
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<CommentTreeNode> addComment(@PathVariable Integer postId,
                                                      @Valid @RequestBody PostCommentCreateRequest request) {
        AppUser currentUser = requireCurrentUser();
        PostComment comment = commentService.addComment(postId, currentUser.getId(), request);
        CommentTreeNode node = CommentTreeNode.builder()
                .id(comment.getId())
                .parentId(comment.getParentId())
                .rootId(comment.getRootId())
                .content(comment.getContent())
                .likeCount(Optional.ofNullable(comment.getLikeCount()).orElse(0))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .author(new com.example.forum.entity.Author(
                        currentUser.getId(),
                        currentUser.getUserName(),
                        currentUser.getAvatarUrl(),
                        currentUser.getBio()
                ))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(node);
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Result<Object>> deleteComment(@PathVariable Integer postId,
                                                        @PathVariable Integer commentId) {
        AppUser currentUser = requireCurrentUser();
        log.info("删除评论：postId={}, commentId={}, operatorId={}", postId, commentId, currentUser.getId());
        commentService.deleteComment(postId, commentId, currentUser);
        return ResponseEntity.ok(Result.success(null));
    }

    @PostMapping("/posts/{postId}/like")
    public ResponseEntity<Map<String, Object>> likePost(@PathVariable Integer postId) {
        AppUser currentUser = requireCurrentUser();
        postService.likePost(postId, currentUser.getId());
        Post post = postService.getById(postId);
        int likeCount = Optional.ofNullable(post).map(Post::getLikeCount).orElse(0);
        return ResponseEntity.ok(Map.of(
                "liked", true,
                "likeCount", likeCount
        ));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ResponseEntity<Map<String, Object>> unlikePost(@PathVariable Integer postId) {
        AppUser currentUser = requireCurrentUser();
        postService.unlikePost(postId, currentUser.getId());
        Post post = postService.getById(postId);
        int likeCount = Optional.ofNullable(post).map(Post::getLikeCount).orElse(0);
        return ResponseEntity.ok(Map.of(
                "liked", false,
                "likeCount", likeCount
        ));
    }

    @PostMapping("/posts")
    public ResponseEntity<PostDetailView> createPost(@Valid @RequestBody PostCreateRequest request) {
        AppUser currentUser = requireCurrentUser();
        Post post = postService.createPost(request, currentUser.getId());
        PostDetailView detail = postService.getPostDetail(post.getId(), currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @GetMapping("/users/me/posts")
    public PageResponse<PostSummaryView> pageMyPosts(@RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @RequestParam(required = false) String status) {
        AppUser currentUser = requireCurrentUser();
        return postService.pageMyPosts(currentUser.getId(), status, page, size);
    }

    private Integer resolveCurrentUserId() {
        return SecurityUtils.getCurrentUsername()
                .map(userService::findByUserName)
                .map(AppUser::getId)
                .orElse(null);
    }

    private AppUser requireCurrentUser() {
        return SecurityUtils.getCurrentUsername()
                .map(userService::findByUserName)
                .filter(user -> user != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "δ��¼"));
    }
}

