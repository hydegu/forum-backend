package com.example.forum.post.controller;

import com.example.forum.post.dto.PostCreateRequest;
import com.example.forum.post.entity.Post;
import com.example.forum.post.service.PostService;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.PostDetailView;
import com.example.forum.post.vo.PostListResponse;
import com.example.forum.post.vo.PostSummaryView;
import com.example.forum.post.vo.TrendingPostView;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    
    @Value("${com.jwt.user-secret-key:}")
    private String jwtSecretKey;
    
    @Value("${com.jwt.user-token-name:authentication}")
    private String jwtTokenName;

    @GetMapping("/posts")
    public PostListResponse getApprovedPosts(@RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(required = false) Integer categoryId) {
        Integer current = Optional.ofNullable(page).orElse(1);
        Integer pageSize = Optional.ofNullable(size).orElse(10);
        Integer currentUserId = resolveCurrentUserId();
        return postService.pageWithSummary(current, pageSize, null,
                status, q, categoryId, currentUserId);
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
        Post post = postService.createPost(request, currentUserId);
        PostDetailView detail = postService.getPostDetail(post.getId(), currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(detail);
    }

    @GetMapping("/users/me/posts")
    public PageResponse<PostSummaryView> pageMyPosts(@RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer size,
                                                     @RequestParam(required = false) String status) {
        Integer currentUserId = requireCurrentUserId();
        return postService.pageMyPosts(currentUserId, status, page, size);
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

    private Integer requireCurrentUserId() {
        Integer userId = resolveCurrentUserId();
        if (userId == null) {
            throw new RuntimeException("Unauthorized");
        }
        return userId;
    }
}