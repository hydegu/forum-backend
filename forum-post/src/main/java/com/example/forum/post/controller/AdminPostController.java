package com.example.forum.post.controller;

import com.example.forum.post.entity.PostLike;
import com.example.forum.post.repo.PostLikeRepo;
import com.example.forum.post.service.PostService;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.AdminPostDetailView;
import com.example.forum.post.vo.AdminPostSummary;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * 管理员帖子控制器
 */
@RestController
@RequestMapping("/api/admin/posts")
@RequiredArgsConstructor
@Slf4j
public class AdminPostController {

    private final PostService postService;
    private final PostLikeRepo postLikeRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${com.jwt.user-secret-key:}")
    private String jwtSecretKey;
    
    @Value("${com.jwt.user-token-name:authentication}")
    private String jwtTokenName;

    @GetMapping
    public PageResponse<AdminPostSummary> pagePosts(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") Integer page,
                                                    @RequestParam(defaultValue = "50") Integer size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String categoryId) {
        Integer currentUserId = resolveCurrentUserId();
        // 注意：暂时允许非管理员访问，生产环境需要添加管理员验证
        // ensureAdmin(currentUserId);
        
        if (!StringUtils.hasText(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        Integer parsedCategoryId = parseCategoryId(categoryId);
        PageResponse<AdminPostSummary> result = postService.pageAdminPosts(
                status.trim(),
                page,
                size,
                keyword,
                parsedCategoryId
        );
        log.info("管理员 {} 查询帖子列表 status={}, page={}, size={}, keyword={}, categoryId={}",
                currentUserId, status, page, size, keyword, categoryId);
        return result;
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<AdminPostSummary> approvePost(@PathVariable Integer postId) {
        Integer currentUserId = resolveCurrentUserId();
        // ensureAdmin(currentUserId);
        AdminPostSummary summary = postService.approvePost(postId);
        log.info("管理员 {} 审核通过帖子 {}", currentUserId, postId);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/{postId}/reject")
    public ResponseEntity<AdminPostSummary> rejectPost(@PathVariable Integer postId) {
        Integer currentUserId = resolveCurrentUserId();
        // ensureAdmin(currentUserId);
        AdminPostSummary summary = postService.rejectPost(postId);
        log.info("管理员 {} 拒绝帖子 {}", currentUserId, postId);
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable Integer postId) {
        Integer currentUserId = resolveCurrentUserId();
        // ensureAdmin(currentUserId);
        postService.deletePostAsAdmin(postId);
        log.info("管理员 {} 删除帖子 {}", currentUserId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{postId}")
    public AdminPostDetailView getPost(@PathVariable Integer postId,
                                       @RequestParam(name = "includeComments", required = false) Boolean includeComments) {
        Integer currentUserId = resolveCurrentUserId();
        // ensureAdmin(currentUserId);
        boolean withComments = Boolean.TRUE.equals(includeComments);
        AdminPostDetailView detail = postService.getAdminPost(postId, withComments);
        log.info("管理员 {} 查看帖子详情 {} includeComments={}", currentUserId, postId, withComments);
        return detail;
    }

    private Integer parseCategoryId(String categoryId) {
        if (!StringUtils.hasText(categoryId)) {
            return null;
        }
        try {
            return Integer.valueOf(categoryId.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid categoryId");
        }
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
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }
    
    private void ensureAdmin(Integer userId) {
        // TODO: 验证用户是否为管理员
        // 生产环境需要实现真正的权限验证
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
    }

    /**
     * 数据同步修复接口 - 从MySQL重建点赞关系Redis缓存
     * 用于修复Redis和MySQL数据不一致的问题
     */
    @PostMapping("/sync/likes")
    public ResponseEntity<Map<String, Object>> syncLikesData() {
        log.info("开始执行点赞数据同步修复...");
        
        try {
            // 1. 清空所有Redis点赞缓存
            Set<String> likesKeys = redisTemplate.keys("post:likes:*");
            
            int deletedKeys = 0;
            if (likesKeys != null && !likesKeys.isEmpty()) {
                deletedKeys = likesKeys.size();
                redisTemplate.delete(likesKeys);
                log.info("已清除 {} 个 post:likes:* keys", deletedKeys);
            }
            
            // 2. 从MySQL读取所有点赞关系
            List<PostLike> allLikes = postLikeRepo.selectList(null);
            log.info("从MySQL读取到 {} 条点赞关系", allLikes.size());
            
            // 3. 重建Redis缓存
            int syncedCount = 0;
            for (PostLike like : allLikes) {
                if (like.getPostId() != null && like.getUserId() != null) {
                    String likesKey = "post:likes:" + like.getPostId();
                    redisTemplate.opsForSet().add(likesKey, like.getUserId().toString());
                    syncedCount++;
                }
            }
            
            log.info("点赞数据同步完成，已同步 {} 条记录到Redis", syncedCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "点赞数据同步成功");
            result.put("deletedKeys", deletedKeys);
            result.put("totalLikes", allLikes.size());
            result.put("syncedCount", syncedCount);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("点赞数据同步失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "数据同步失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 查看点赞数据同步状态
     */
    @GetMapping("/sync/likes/status")
    public ResponseEntity<Map<String, Object>> getLikesDataStatus() {
        try {
            // MySQL数据统计
            List<PostLike> allLikes = postLikeRepo.selectList(null);
            int mysqlCount = allLikes.size();
            
            // Redis缓存统计
            Set<String> likesKeys = redisTemplate.keys("post:likes:*");
            int redisKeys = likesKeys != null ? likesKeys.size() : 0;
            
            // 统计Redis中的总点赞数
            int redisTotalLikes = 0;
            if (likesKeys != null) {
                for (String key : likesKeys) {
                    Long size = redisTemplate.opsForSet().size(key);
                    redisTotalLikes += (size != null ? size.intValue() : 0);
                }
            }
            
            Map<String, Object> status = new HashMap<>();
            status.put("mysqlTotalLikes", mysqlCount);
            status.put("redisLikesKeys", redisKeys);
            status.put("redisTotalLikes", redisTotalLikes);
            status.put("suggestion", mysqlCount > 0 && redisKeys == 0 
                ? "检测到Redis缓存为空，建议执行数据同步" 
                : "数据状态正常");
            status.put("consistency", mysqlCount == redisTotalLikes ? "一致" : "不一致");
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("获取点赞数据状态失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "获取状态失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
    
    /**
     * 清空所有Redis指标缓存（浏览数、点赞数、评论数）
     * 用于重置增量计数器
     */
    @PostMapping("/sync/metrics/reset")
    public ResponseEntity<Map<String, Object>> resetPostMetrics() {
        try {
            Set<String> metricsKeys = redisTemplate.keys("post:metrics:*");
            
            int deletedKeys = 0;
            if (metricsKeys != null && !metricsKeys.isEmpty()) {
                deletedKeys = metricsKeys.size();
                redisTemplate.delete(metricsKeys);
                log.info("已清除 {} 个 post:metrics:* keys", deletedKeys);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "帖子指标缓存已清空");
            result.put("deletedKeys", deletedKeys);
            result.put("note", "增量计数器已重置，下次访问将从MySQL读取基准值");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("清空指标缓存失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "清空失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
