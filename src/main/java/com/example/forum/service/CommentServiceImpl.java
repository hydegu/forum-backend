package com.example.forum.service;

import com.example.forum.dto.PostCommentCreateRequest;
import com.example.forum.entity.AppUser;
import com.example.forum.entity.Author;
import com.example.forum.entity.Post;
import com.example.forum.entity.PostComment;
import com.example.forum.exception.ApiException;
import com.example.forum.repo.PostCommentRepo;
import com.example.forum.repo.PostRepo;
import com.example.forum.repo.UserRepo;
import com.example.forum.vo.CommentTreeNode;
import com.example.forum.vo.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
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
    private final PostRepo postRepo;
    private final UserRepo userRepo;
    private final CacheManager cacheManager;
    private final RedisTemplate<String, Object> redisTemplate;
    @Override
    @Cacheable(cacheNames = "comments:page",key="#postId + ':' + #page + ':' + #size")
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
        log.info("即将返回评论/回复:评论树:{}",rootNodes);
        return PageResponse.of(rootNodes, total, safePage, safeSize, totalPages, Map.of("total", total));
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostComment addComment(Integer postId, Integer userId, PostCommentCreateRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException();
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException();
        }
        Post post = postRepo.selectById(postId);
        if (post == null) {
            throw new NoSuchElementException();
        }
        AppUser user = userRepo.selectById(userId);
        if (user == null) {
            throw new NoSuchElementException();
        }
        Integer parentId = request.getParentId();
        Integer rootId = null;
        if (parentId != null) {
            PostComment parent = postCommentRepo.selectById(parentId);
            if (parent == null || !postId.equals(parent.getPostId())) {
                throw new IllegalArgumentException("鐖剁骇璇勮涓嶅瓨鍦ㄦ垨宸茶鍒犻櫎");
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

        // 清除该帖子的评论缓存（只影响当前帖子）
        evictPostCommentCache(postId);

        // 边界处理：立即刷新第一页缓存，减少短期不一致
        refreshFirstPageCache(postId);

        return comment;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Integer postId, Integer commentId, AppUser operator) {
        if (postId == null || commentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请求参数无效");
        }
        if (operator == null || operator.getId() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录或会话已失效");
        }
        Post post = postRepo.selectById(postId);
        if (post == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "帖子不存在");
        }
        PostComment comment = postCommentRepo.selectById(commentId);
        if (comment == null || Boolean.TRUE.equals(comment.getDeleted())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "评论不存在或已被删除");
        }
        if (!postId.equals(comment.getPostId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "评论不存在");
        }
        boolean isCommentOwner = comment.getUserId() != null && comment.getUserId().equals(operator.getId());
        boolean isPostOwner = post.getAuthorId() != null && post.getAuthorId().equals(operator.getId());
        String role = operator.getRole();
        boolean isAdmin = StringUtils.hasText(role) && role.toLowerCase(Locale.ROOT).contains("admin");
        if (!isCommentOwner && !isPostOwner && !isAdmin) {
            log.warn("用户试图删除无权限的评论，commentId={}，operatorId={}", commentId, operator.getId());
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
                commentId, postId, operator.getId(), affected);

        // 清除该帖子的评论缓存（只影响当前帖子）
        evictPostCommentCache(postId);

        // 边界处理：立即刷新第一页缓存，减少短期不一致
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
        String content = deleted ? "[璇ヨ瘎璁哄凡鍒犻櫎]" : comment.getContent();
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
    private void incrementPostCommentCount(Integer postId, int delta) {
        // 1. 更新 Redis metrics（与浏览量、点赞数保持一致）
        try {
            String metricsKey = "post:metrics:" + postId;
            redisTemplate.opsForHash().increment(metricsKey, "comments", delta);
            log.debug("更新Redis评论数: postId={}, delta={}", postId, delta);
        } catch (Exception e) {
            log.warn("更新Redis评论数失败: postId={}, delta={}, error={}", postId, delta, e.getMessage());
        }

        // 2. 更新数据库（保持原有逻辑，用于数据持久化）
        int affected = postRepo.updateCommentCount(postId, delta);
        if (affected == 0) {
            log.warn("评论数量持久化更新失败,帖子ID： postId={}, delta={}", postId, delta);
        }
    }

    /**
     * 清除特定帖子的所有评论缓存
     * 只清除该 postId 相关的缓存，不影响其他帖子
     */
    private void evictPostCommentCache(Integer postId) {
        try {
            Cache cache = cacheManager.getCache("comments:page");
            if (cache != null) {
                // 清除常用的分页组合（前3页，常用的size）
                int[] pages = {1, 2, 3};
                int[] sizes = {10, 20, 50};

                for (int page : pages) {
                    for (int size : sizes) {
                        String cacheKey = postId + ":" + page + ":" + size;
                        cache.evict(cacheKey);
                    }
                }
                log.debug("已清除帖子评论缓存：postId={}", postId);
            }
        } catch (Exception e) {
            log.warn("清除评论缓存失败：postId={}, error={}", postId, e.getMessage());
        }
    }

    /**
     * 刷新评论第一页缓存，减少短期不一致
     * 在添加或删除评论后调用，主动预热缓存
     */
    private void refreshFirstPageCache(Integer postId) {
        try {
            // 默认第一页的参数：页码1，每页10条（可根据实际业务调整）
            int defaultPage = 1;
            int defaultSize = 10;

            // 查询最新的第一页数据
            PageResponse<CommentTreeNode> firstPage = pageComments(postId, defaultPage, defaultSize);

            // 使用 CacheManager 手动写入缓存
            Cache cache = cacheManager.getCache("comments:page");
            if (cache != null) {
                String cacheKey = postId + ":" + defaultPage + ":" + defaultSize;
                cache.put(cacheKey, firstPage);
                log.debug("已刷新评论缓存：postId={}, cacheKey={}", postId, cacheKey);
            }
        } catch (Exception e) {
            // 缓存刷新失败不影响主流程，仅记录日志
            log.warn("刷新评论缓存失败：postId={}, error={}", postId, e.getMessage());
        }
    }
}
