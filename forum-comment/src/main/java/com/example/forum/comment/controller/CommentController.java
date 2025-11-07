package com.example.forum.comment.controller;

import com.example.forum.comment.dto.PostCommentCreateRequest;
import com.example.forum.comment.entity.PostComment;
import com.example.forum.comment.service.CommentService;
import com.example.forum.comment.vo.CommentTreeNode;
import com.example.forum.common.dto.Result;
import com.example.forum.common.vo.PageResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

/**
 * 评论控制器
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    
    @Value("${com.jwt.user-secret-key:}")
    private String jwtSecretKey;
    
    @Value("${com.jwt.user-token-name:authentication}")
    private String jwtTokenName;

    // 处理 /api/posts/{postId}/comments 路径（兼容单体应用的路径）
    @GetMapping("/api/posts/{postId}/comments")
    public PageResponse<CommentTreeNode> pageCommentsForPost(@PathVariable Integer postId,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "10") Integer size) {
        return commentService.pageComments(postId, page, size);
    }

    // 处理 /api/comments/posts/{postId} 路径（供内部调用）
    @GetMapping("/api/comments/posts/{postId}")
    public PageResponse<CommentTreeNode> pageComments(@PathVariable Integer postId,
                                                      @RequestParam(defaultValue = "1") Integer page,
                                                      @RequestParam(defaultValue = "10") Integer size) {
        return commentService.pageComments(postId, page, size);
    }
    
    /**
     * 供帖子服务调用，返回Object类型以兼容不同微服务
     */
    @GetMapping("/api/comments/posts/{postId}/for-post-service")
    public Result<PageResponse<Object>> getCommentsForPostService(@PathVariable Integer postId,
                                                                   @RequestParam(defaultValue = "1") Integer page,
                                                                   @RequestParam(defaultValue = "50") Integer size) {
        PageResponse<CommentTreeNode> comments = commentService.pageComments(postId, page, size);
        // 将CommentTreeNode转换为Object（实际上可以直接返回，因为Feign会自动序列化）
        PageResponse<Object> result = PageResponse.of(
                new java.util.ArrayList<>(comments.getRecords()),
                comments.getTotal(),
                comments.getPage(),
                comments.getSize(),
                comments.getTotalPages(),
                comments.getExtra()
        );
        return Result.success(result);
    }

    // 处理 /api/posts/{postId}/comments POST（兼容单体应用的路径）
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentTreeNode> addCommentForPost(@PathVariable Integer postId,
                                                              @Valid @RequestBody PostCommentCreateRequest request) {
        // TODO: 从JWT token中解析用户ID
        Integer currentUserId = resolveCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        PostComment comment = commentService.addComment(postId, currentUserId, request);
        CommentTreeNode node = CommentTreeNode.builder()
                .id(comment.getId())
                .parentId(comment.getParentId())
                .rootId(comment.getRootId())
                .content(comment.getContent())
                .likeCount(Optional.ofNullable(comment.getLikeCount()).orElse(0))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .author(new com.example.forum.comment.entity.Author(
                        comment.getUserId(),
                        comment.getAuthorName(),
                        comment.getAuthorAvatar(),
                        null
                ))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(node);
    }

    // 处理 /api/comments/posts/{postId} POST（供内部调用）
    @PostMapping("/api/comments/posts/{postId}")
    public ResponseEntity<CommentTreeNode> addComment(@PathVariable Integer postId,
                                                      @Valid @RequestBody PostCommentCreateRequest request) {
        // TODO: 从JWT token中解析用户ID
        Integer currentUserId = resolveCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        PostComment comment = commentService.addComment(postId, currentUserId, request);
        CommentTreeNode node = CommentTreeNode.builder()
                .id(comment.getId())
                .parentId(comment.getParentId())
                .rootId(comment.getRootId())
                .content(comment.getContent())
                .likeCount(Optional.ofNullable(comment.getLikeCount()).orElse(0))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .author(new com.example.forum.comment.entity.Author(
                        comment.getUserId(),
                        comment.getAuthorName(),
                        comment.getAuthorAvatar(),
                        null
                ))
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(node);
    }

    // 处理 /api/posts/{postId}/comments/{commentId} DELETE（兼容单体应用的路径）
    @DeleteMapping("/api/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Result<Object>> deleteCommentForPost(@PathVariable Integer postId,
                                                                @PathVariable Integer commentId) {
        // TODO: 从JWT token中解析用户ID和角色
        Integer currentUserId = resolveCurrentUserId();
        String currentUserRole = resolveCurrentUserRole();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("删除评论：postId={}, commentId={}, operatorId={}", postId, commentId, currentUserId);
        commentService.deleteComment(postId, commentId, currentUserId, currentUserRole);
        return ResponseEntity.ok(Result.success(null));
    }

    // 处理 /api/comments/posts/{postId}/comments/{commentId} DELETE（供内部调用）
    @DeleteMapping("/api/comments/posts/{postId}/comments/{commentId}")
    public ResponseEntity<Result<Object>> deleteComment(@PathVariable Integer postId,
                                                        @PathVariable Integer commentId) {
        // TODO: 从JWT token中解析用户ID和角色
        Integer currentUserId = resolveCurrentUserId();
        String currentUserRole = resolveCurrentUserRole();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("删除评论：postId={}, commentId={}, operatorId={}", postId, commentId, currentUserId);
        commentService.deleteComment(postId, commentId, currentUserId, currentUserRole);
        return ResponseEntity.ok(Result.success(null));
    }

    /**
     * 从JWT token中解析当前用户ID
     * 支持从请求头(Authorization)或Cookie中获取token
     */
    private Integer resolveCurrentUserId() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                return null;
            }
            
            // 1. 从请求头获取token
            String token = extractTokenFromHeader(request);
            
            // 2. 如果请求头没有，从Cookie获取
            if (token == null) {
                token = extractTokenFromCookie(request);
            }
            
            if (token == null) {
                return null;
            }
            
            // 3. 解析JWT token获取userId
            return parseUserIdFromToken(token);
            
        } catch (Exception e) {
            log.debug("解析用户ID失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从JWT token中解析当前用户角色
     */
    private String resolveCurrentUserRole() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                return null;
            }
            
            // 1. 从请求头获取token
            String token = extractTokenFromHeader(request);
            
            // 2. 如果请求头没有，从Cookie获取
            if (token == null) {
                token = extractTokenFromCookie(request);
            }
            
            if (token == null) {
                return null;
            }
            
            // 3. 解析JWT token获取role
            return parseUserRoleFromToken(token);
            
        } catch (Exception e) {
            log.debug("解析用户角色失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 从请求头中提取JWT token
     */
    private String extractTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            // 尝试使用配置的token名称
            header = request.getHeader(jwtTokenName);
        }
        
        if (!StringUtils.hasText(header)) {
            return null;
        }
        
        // 支持 "Bearer token" 格式
        if (header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7);
        }
        
        return header;
    }
    
    /**
     * 从Cookie中提取JWT token
     */
    private String extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        
        return Arrays.stream(request.getCookies())
                .filter(cookie -> jwtTokenName.equals(cookie.getName()))
                .map(cookie -> cookie.getValue())
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 解析JWT token获取userId
     */
    private Integer parseUserIdFromToken(String token) {
        if (!StringUtils.hasText(jwtSecretKey)) {
            log.warn("JWT密钥未配置，无法解析token");
            return null;
        }
        
        try {
            Claims claims = parseJWT(jwtSecretKey, token);
            Object userIdObj = claims.get("userId");
            
            if (userIdObj == null) {
                return null;
            }
            
            // 支持多种类型转换
            if (userIdObj instanceof Integer) {
                return (Integer) userIdObj;
            } else if (userIdObj instanceof Number) {
                return ((Number) userIdObj).intValue();
            } else {
                return Integer.parseInt(userIdObj.toString());
            }
            
        } catch (JwtException e) {
            log.debug("JWT token解析失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("解析userId失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 解析JWT token获取用户角色
     */
    private String parseUserRoleFromToken(String token) {
        if (!StringUtils.hasText(jwtSecretKey)) {
            return null;
        }
        
        try {
            Claims claims = parseJWT(jwtSecretKey, token);
            Object roleObj = claims.get("role");
            
            if (roleObj == null) {
                return null;
            }
            
            return roleObj.toString();
            
        } catch (JwtException e) {
            log.debug("JWT token解析失败: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("解析用户角色失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 解析JWT token
     */
    private Claims parseJWT(String secretKey, String token) {
        return io.jsonwebtoken.Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(secretKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * 获取当前HttpServletRequest
     * 使用RequestContextHolder，这是Spring MVC的标准方式
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            org.springframework.web.context.request.RequestAttributes requestAttributes = 
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            if (requestAttributes instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                return ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes)
                        .getRequest();
            }
            return null;
        } catch (Exception e) {
            log.debug("无法获取当前请求: {}", e.getMessage());
            return null;
        }
    }
}
