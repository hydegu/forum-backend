package com.example.forum.comment.service;

import com.example.forum.comment.client.PostClient;
import com.example.forum.comment.client.UserClient;
import com.example.forum.comment.dto.PostCommentCreateRequest;
import com.example.forum.comment.entity.Author;
import com.example.forum.comment.entity.PostComment;
import com.example.forum.comment.repo.PostCommentRepo;
import com.example.forum.common.dto.Result;
import com.example.forum.common.exception.ApiException;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.comment.vo.CommentTreeNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    
    private final PostCommentRepo postCommentRepo;
    private final PostClient postClient;
    private final UserClient userClient;
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Cacheable(cacheNames = "comments:page", key = "#postId + ':' + #page + ':' + #size")
    public PageResponse<CommentTreeNode> pageComments(Integer postId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(Math.min(size, 50), 1);
        long total = postCommentRepo.countRootComments(postId);
        if (total == 0) {
            return PageResponse.of(Collections.emptyList(), 0, safePage, safeSize, 0, Map.of("total", 0));
        }
        long offset = (long) (safePage - 1) * safeSize;
        List<PostComment> rootComments = postCommentRepo.selectRootComments(postId, offset, safeSize);
        if (CollectionUtils.isEmpty(rootComments)) {
            long totalPages = calculateTotalPages(total, safeSize);
            return PageResponse.of(Collections.emptyList(), total, safePage, safeSize, totalPages, Map.of("total", total));
        }
        Map<Integer, CommentTreeNode> nodeMap = new LinkedHashMap<>();
        List<CommentTreeNode> rootNodes = new ArrayList<>();
        List<Integer> rootIds = new ArrayList<>();
        for (PostComment comment : rootComments) {
            CommentTreeNode node = toView(comment);
            Integer rootId = Optional.ofNullable(comment.getRootId()).orElse(comment.getId());
            node.setRootId(rootId);
            nodeMap.put(comment.getId(), node);
            rootNodes.add(node);
            rootIds.add(rootId);
        }
        if (!rootIds.isEmpty()) {
            List<Integer> distinctRootIds = rootIds.stream().distinct().toList();
            List<PostComment> replies = postCommentRepo.selectRepliesByRootIds(postId, distinctRootIds);
            for (PostComment reply : replies) {
                CommentTreeNode replyNode = toView(reply);
                Integer rootId = Optional.ofNullable(reply.getRootId()).orElse(reply.getId());
                replyNode.setRootId(rootId);
                nodeMap.put(reply.getId(), replyNode);
            }
            for (PostComment reply : replies) {
                CommentTreeNode current = nodeMap.get(reply.getId());
                Integer parentKey = reply.getParentId() != null ? reply.getParentId() : reply.getRootId();
                CommentTreeNode parent = parentKey != null ? nodeMap.get(parentKey) : null;
                if (parent != null && current != null) {
                    parent.getReplies().add(current);
                } else {
                    log.debug("取得的评论ID：, commentId={}, parentId={}", reply.getId(), reply.getParentId());
                }
            }
            nodeMap.values().forEach(node ->
                    node.getReplies().sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
            );
        }
        long totalPages = calculateTotalPages(total, safeSize);
        return PageResponse.of(rootNodes, total, safePage, safeSize, totalPages, Map.of("total", total));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostComment addComment(Integer postId, Integer userId, PostCommentCreateRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("评论内容不能为空");
        }

        // 通过Feign调用帖子服务检查帖子是否存在
        Result<Boolean> postExistsResult = postClient.checkPostExists(postId);
        if (postExistsResult == null || !Boolean.TRUE.equals(postExistsResult.getResult())) {
            throw new NoSuchElementException("帖子不存在");
        }

        // 通过Feign调用用户服务检查用户是否存在
        Result<Boolean> userExistsResult = userClient.checkUserExists(userId);
        if (userExistsResult == null || !Boolean.TRUE.equals(userExistsResult.getResult())) {
            throw new NoSuchElementException("用户不存在");
        }

        Integer parentId = request.getParentId();
        Integer rootId = null;
        if (parentId != null) {
            PostComment parent = postCommentRepo.selectById(parentId);
            if (parent == null || !postId.equals(parent.getPostId())) {
                throw new IllegalArgumentException("父级评论不存在或已被删除");
            }
            rootId = parent.getRootId() != null ? parent.getRootId() : parent.getId();
        }
        LocalDateTime now = LocalDateTime.now();
        PostComment comment = new PostComment()
                .setPostId(postId)
                .setUserId(userId)
                .setParentId(parentId)
                .setRootId(rootId)
                .setContent(request.getContent().trim())
                .setLikeCount(0)
                .setDeleted(Boolean.FALSE)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        postCommentRepo.insert(comment);
        if (comment.getRootId() == null) {
            comment.setRootId(comment.getId());
            PostComment update = new PostComment()
                    .setId(comment.getId())
                    .setRootId(comment.getId());
            postCommentRepo.updateById(update);
        }
        incrementPostCommentCount(postId, 1);

        evictPostCommentCache(postId);
        refreshFirstPageCache(postId);

        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Integer postId, Integer commentId, Integer operatorId, String operatorRole) {
        if (postId == null || commentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请求参数无效");
        }
        if (operatorId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        }

        // 通过Feign调用帖子服务检查帖子是否存在并获取作者ID
        Result<Integer> authorIdResult = postClient.getPostAuthorId(postId);
        if (authorIdResult == null || authorIdResult.getResult() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        Integer postAuthorId = authorIdResult.getResult();

        PostComment comment = postCommentRepo.selectById(commentId);
        if (comment == null || Boolean.TRUE.equals(comment.getDeleted())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "评论不存在或已被删除");
        }
        if (!postId.equals(comment.getPostId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "评论不存在");
        }
        boolean isCommentOwner = comment.getUserId() != null && comment.getUserId().equals(operatorId);
        boolean isPostOwner = postAuthorId != null && postAuthorId.equals(operatorId);
        boolean isAdmin = StringUtils.hasText(operatorRole) && operatorRole.toLowerCase(Locale.ROOT).contains("admin");
        if (!isCommentOwner && !isPostOwner && !isAdmin) {
            log.warn("用户试图删除无权限的评论，commentId={}，operatorId={}", commentId, operatorId);
            throw new ApiException(HttpStatus.FORBIDDEN, "无权删除该评论");
        }
        Integer rootId = Optional.ofNullable(comment.getRootId()).orElse(comment.getId());
        LocalDateTime now = LocalDateTime.now();
        int affected = postCommentRepo.softDeleteThread(postId, rootId, now);
        if (affected <= 0) {
            log.error("评论删除失败，commentId={}，postId={}", commentId, postId);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "删除评论失败");
        }
        incrementPostCommentCount(postId, -affected);
        log.info("评论删除成功，commentId={}，postId={}，operatorId={}，影响行数={}",
                commentId, postId, operatorId, affected);

        evictPostCommentCache(postId);
        refreshFirstPageCache(postId);
    }

    private CommentTreeNode toView(PostComment comment) {
        Author author = new Author(
                comment.getUserId(),
                comment.getAuthorName(),
                comment.getAuthorAvatar(),
                null
        );
        boolean deleted = Boolean.TRUE.equals(comment.getDeleted());
        String content = deleted ? "[该评论已被删除]" : comment.getContent();
        return CommentTreeNode.builder()
                .id(comment.getId())
                .parentId(comment.getParentId())
                .rootId(comment.getRootId())
                .content(content)
                .likeCount(Optional.ofNullable(comment.getLikeCount()).orElse(0))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .author(author)
                .build();
    }

    private long calculateTotalPages(long total, int size) {
        return (total + size - 1) / size;
    }

    /**
     * 更新帖子评论数（仅更新Redis增量，不更新MySQL）
     * 
     * 策略：使用 INCR 原子操作更新 Redis 增量，定时任务 PostMetricsSyncJob 每5分钟持久化到 MySQL
     * Redis Hash: post:metrics:{postId} -> {comments: 增量值}
     * 
     * 与浏览量、点赞保持一致的增量模式
     */
    private void incrementPostCommentCount(Integer postId, int delta) {
        String metricsKey = "post:metrics:" + postId;
        
        // 使用原子 INCR 操作更新 Redis 增量
        try {
            redisTemplate.opsForHash().increment(metricsKey, "comments", delta);
            log.debug("增量更新Redis评论数: postId={}, delta={} (MySQL将由定时任务同步)", postId, delta);
        } catch (Exception e) {
            log.error("更新Redis评论数失败: postId={}, delta={}, error={}", postId, delta, e.getMessage());
        }
    }

    private void evictPostCommentCache(Integer postId) {
        try {
            Cache cache = cacheManager.getCache("comments:page");
            if (cache != null) {
                // 清除所有可能的缓存key组合（常见的page和size组合）
                int[] pages = {1, 2, 3, 4, 5};
                int[] sizes = {10, 20, 50};
                for (int page : pages) {
                    for (int size : sizes) {
                        String cacheKey = postId + ":" + page + ":" + size;
                        cache.evict(cacheKey);
                    }
                }
                log.debug("已清除帖子评论缓存：postId={}", postId);
            } else {
                log.warn("CacheManager中未找到comments:page缓存");
            }
        } catch (Exception e) {
            log.warn("清除评论缓存失败：postId={}, error={}", postId, e.getMessage());
        }
    }

    private void refreshFirstPageCache(Integer postId) {
        try {
            // 先清除第一页缓存，确保重新查询
            Cache cache = cacheManager.getCache("comments:page");
            if (cache != null) {
                int defaultPage = 1;
                int defaultSize = 10;
                String cacheKey = postId + ":" + defaultPage + ":" + defaultSize;
                cache.evict(cacheKey);
                log.debug("已清除第一页评论缓存：postId={}, cacheKey={}", postId, cacheKey);
            }
            // 重新查询并自动缓存（pageComments方法有@Cacheable注解）
            pageComments(postId, 1, 10);
            log.debug("已刷新评论缓存：postId={}", postId);
        } catch (Exception e) {
            log.warn("刷新评论缓存失败：postId={}, error={}", postId, e.getMessage());
        }
    }
}
