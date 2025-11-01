package com.example.forum.post.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.forum.post.client.UserClient;
import com.example.forum.post.client.CommentClient;
import com.example.forum.post.dto.PostCreateRequest;
import com.example.forum.post.entity.Author;
import com.example.forum.post.entity.Post;
import com.example.forum.post.repo.PostLikeRepo;
import com.example.forum.post.repo.PostRepo;
import com.example.forum.common.exception.ApiException;
import com.example.forum.common.vo.PageResponse;
import com.example.forum.post.vo.AdminPostDetailView;
import com.example.forum.post.vo.AdminPostSummary;
import com.example.forum.post.vo.CategoryResponse;
import com.example.forum.post.vo.PostDetailView;
import com.example.forum.post.vo.PostListResponse;
import com.example.forum.post.vo.PostSummaryView;
import com.example.forum.post.vo.TrendingPostView;
import com.example.forum.common.dto.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostServiceImpl extends ServiceImpl<PostRepo, Post> implements PostService {

    private final PostLikeRepo postLikeRepo;
    private final UserClient userClient;
    private final CommentClient commentClient;
    private final CategoryService categoryService;
    private final PostLikeService postLikeService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Page<Post> pageWithAuthorName(Integer current, Integer size,
                                         String status, String q, Integer categoryId) {
        Page<Post> page = Page.of(Math.max(1, current), Math.min(size, 100));
        return baseMapper.selectPageWithAuthorAndCategory(page, null, status, q, categoryId);
    }

    @Override
    public PostListResponse pageWithSummary(Integer current, Integer pageSize, Wrapper<Post> wrapper,
                                            String status, String q, Integer categoryId, Integer currentUserId) {
        Page<Post> pageRequest = Page.of(current, pageSize);
        Page<Post> pageResult = baseMapper.selectPageSummaryWithAuthor(pageRequest, null, status, q, categoryId);
        log.debug("Page query completed - total records: {}, current page: {}/{}",
                pageResult.getTotal(), pageResult.getCurrent(), pageResult.getPages());

        List<Post> postRecords = pageResult.getRecords();
        List<PostSummaryView> records = enrichSummaryViews(postRecords, currentUserId);

        return new PostListResponse(
                records,
                pageResult.getTotal(),
                Map.of("total", pageResult.getTotal()),
                pageResult.getPages(),
                pageResult.getCurrent() >= pageResult.getPages()
        );
    }

    @Override
    public PageResponse<AdminPostSummary> pageAdminPosts(String status, int page, int size, String keyword, Integer categoryId) {
        String normalizedStatus = normalizeModerationStatus(status);
        if (!StringUtils.hasText(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 50);
        String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Post> pageResult = baseMapper.selectPageSummaryWithAuthor(
                Page.of(current, pageSize),
                null,
                normalizedStatus,
                trimmedKeyword,
                categoryId
        );
        List<AdminPostSummary> records = pageResult.getRecords()
                .stream()
                .map(this::toAdminPostSummary)
                .toList();
        return PageResponse.of(
                records,
                pageResult.getTotal(),
                current,
                pageSize,
                pageResult.getPages(),
                Collections.emptyMap()
        );
    }

    @Override
    public AdminPostSummary approvePost(Integer postId) {
        return moderatePostStatus(postId, "approved");
    }

    @Override
    public AdminPostSummary rejectPost(Integer postId) {
        return moderatePostStatus(postId, "rejected");
    }

    @Override
    public List<TrendingPostView> getTrendingPostViews(int limit) {
        List<TrendingPostView> cachedTrending = getTrendingFromRedis(limit);
        if (!cachedTrending.isEmpty()) {
            log.debug("从 Redis 缓存返回热门帖子，数量: {}", cachedTrending.size());
            return cachedTrending;
        }

        log.debug("Redis 缓存为空，从数据库查询热门帖子");
        LambdaQueryWrapper<Post> wrapper = Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, "approved")
                .orderByDesc(Post::getHeat)
                .last("LIMIT " + limit);
        List<Post> posts = this.list(wrapper);

        syncTrendingPostsToRedis(posts);

        return posts.stream()
                .map(post -> new TrendingPostView(
                        String.valueOf(post.getId()),
                        post.getTitle(),
                        Optional.ofNullable(post.getHeat()).orElse(0)
                ))
                .toList();
    }

    @Override
    public PostDetailView getPostDetail(Integer postId, Integer currentUserId) {
        incrementViewCount(postId);
        return getPostDetailCached(postId, currentUserId);
    }

    @Cacheable(cacheNames = "posts:detail", key = "#postId + ':' + (#currentUserId != null ? #currentUserId : 'anon')")
    public PostDetailView getPostDetailCached(Integer postId, Integer currentUserId) {
        Post post = baseMapper.selectByIdWithAuthor(postId);
        if (post == null) {
            throw new NoSuchElementException("帖子不存在");
        }
        boolean liked = currentUserId != null && isPostLikedByUser(postId, currentUserId);
        boolean followed = currentUserId != null && isAuthorFollowedBy(post.getAuthorId(), currentUserId);
        List<String> images = Optional.ofNullable(post.getImages()).orElse(Collections.emptyList());

        int viewCount = getMetricFromRedis(postId, "views", post.getViewCount());
        int likeCount = getMetricFromRedis(postId, "likes", post.getLikeCount());
        int commentCount = getMetricFromRedis(postId, "comments", post.getCommentCount());

        Author author = new Author(
                post.getAuthorId(),
                post.getAuthorName(),
                post.getAuthorAvatar(),
                post.getAuthorBio()
        );

        CategoryResponse category = null;
        if (post.getCategoryId() != null) {
            try {
                category = categoryService.getCategory(post.getCategoryId());
            } catch (Exception e) {
                log.warn("Failed to load category {} for post {}: {}", post.getCategoryId(), postId, e.getMessage());
            }
        }

        return PostDetailView.builder()
                .id(post.getId())
                .title(post.getTitle())
                .subtitle(post.getSubtitle())
                .content(post.getContent())
                .images(images)
                .author(author)
                .category(category)
                .likeCount(likeCount)
                .likes(likeCount)
                .liked(liked)
                .commentCount(commentCount)
                .viewCount(viewCount)
                .followed(followed)
                .pinned(post.getPinned())
                .status(post.getStatus())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    @Override
    public PageResponse<PostSummaryView> pageMyPosts(Integer userId, String status, int page, int size) {
        Page<Post> pageReq = Page.of(Math.max(page, 1), Math.min(size, 100));
        Integer authorId = userId;
        String normalizedStatus = StringUtils.hasText(status) ? status : null;
        Page<Post> pageResult = baseMapper.selectPageSummaryWithAuthor(pageReq, authorId, normalizedStatus, null, null);
        List<PostSummaryView> records = pageResult.getRecords()
                .stream()
                .map(post -> toPostSummaryView(post, false, false))
                .toList();
        return PageResponse.of(
                records,
                pageResult.getTotal(),
                (int) pageResult.getCurrent(),
                (int) pageResult.getSize(),
                pageResult.getPages(),
                Map.of("total", pageResult.getTotal())
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "posts:list", allEntries = true)
    public Post createPost(PostCreateRequest request, Integer authorId) {
        if (authorId != null) {
            Result<Boolean> existsResult = userClient.checkUserExists(authorId);
            if (existsResult == null || !Boolean.TRUE.equals(existsResult.getResult())) {
                throw new NoSuchElementException("用户不存在");
            }
        }
        Post post = new Post()
                .setTitle(request.getTitle())
                .setSubtitle(request.getSubtitle())
                .setContent(request.getContent())
                .setCategoryId(request.getCategoryId())
                .setImages(Optional.ofNullable(request.getImages()).orElse(Collections.emptyList()))
                .setAuthorId(authorId)
                .setStatus(determineStatus(request.getStatus()))
                .setPinned(Boolean.TRUE.equals(request.getPinned()))
                .setViewCount(0)
                .setLikeCount(0)
                .setCommentCount(0)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        this.save(post);
        return post;
    }

    @Override
    public boolean likePost(Integer postId, Integer userId) {
        return postLikeService.likePost(postId, userId);
    }

    @Override
    public boolean unlikePost(Integer postId, Integer userId) {
        return postLikeService.unlikePost(postId, userId);
    }

    @Override
    public boolean isPostLikedByUser(Integer postId, Integer userId) {
        return postLikeService.isPostLikedByUser(postId, userId);
    }

    @Override
    public AdminPostDetailView getAdminPost(Integer postId, boolean includeComments) {
        if (postId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Post id is required");
        }
        Post post = baseMapper.selectByIdWithAuthor(postId);
        if (post == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Post not found");
        }
        AdminPostSummary summary = toAdminPostSummary(post);
        AdminPostSummary.AdminPostAuthor authorView = summary != null ? summary.author() : null;
        AdminPostSummary.AdminPostCategory categoryView = summary != null ? summary.category() : null;
        LocalDateTime createdAtValue = Optional.ofNullable(post.getUpdatedAt()).orElse(post.getCreatedAt());
        String createdAt = toIso(createdAtValue);
        String submittedAt = toIso(post.getCreatedAt());
        // 通过Feign调用评论服务获取评论列表
        List<Object> comments = null;
        if (includeComments) {
            try {
                Result<com.example.forum.common.vo.PageResponse<Object>> result = commentClient.getComments(postId, 1, 50);
                if (result != null && result.getResult() != null && result.getResult().getRecords() != null) {
                    comments = result.getResult().getRecords();
                }
            } catch (Exception e) {
                log.warn("调用评论服务获取评论列表失败: postId={}, error={}", postId, e.getMessage());
            }
        }
        return new AdminPostDetailView(
                post.getId() != null ? String.valueOf(post.getId()) : null,
                post.getTitle(),
                authorView,
                formatAdminStatus(post.getStatus()),
                categoryView,
                post.getContent(),
                createdAt,
                submittedAt,
                comments,
                post
        );
    }

    @Override
    @Caching(evict = {
            @CacheEvict(cacheNames = "posts:detail", allEntries = true),
            @CacheEvict(cacheNames = "posts:list", allEntries = true)
    })
    public void deletePostAsAdmin(Integer postId) {
        if (postId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Post id is required");
        }
        Post existing = this.getById(postId);
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Post not found");
        }
        boolean removed = this.removeById(postId);
        if (!removed) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete post");
        }
        log.info("Admin deleted post {}", postId);
    }

    /**
     * 递增帖子浏览量（仅更新Redis增量，不更新MySQL）
     * 
     * 策略：使用 INCR 原子操作更新 Redis 增量，定时任务每5分钟持久化到 MySQL
     * Redis Hash: post:metrics:{postId} -> {views: 增量值}
     */
    private void incrementViewCount(Integer postId) {
        try {
            String metricsKey = "post:metrics:" + postId;
            // 使用原子 INCR 操作
            redisTemplate.opsForHash().increment(metricsKey, "views", 1);
            log.debug("增量递增帖子浏览量: postId={}, delta=+1 (MySQL将由定时任务同步)", postId);
        } catch (Exception e) {
            log.warn("更新浏览量失败: postId={}, error={}", postId, e.getMessage());
        }
    }

    private boolean isAuthorFollowedBy(Integer authorId, Integer followerId) {
        if (authorId == null || followerId == null) {
            return false;
        }
        try {
            Result<Boolean> result = userClient.isFollowing(followerId, authorId);
            return result != null && Boolean.TRUE.equals(result.getResult());
        } catch (Exception e) {
            log.warn("调用用户服务检查关注关系失败: followerId={}, authorId={}", followerId, authorId, e);
            return false;
        }
    }

    private List<PostSummaryView> enrichSummaryViews(List<Post> posts, Integer currentUserId) {
        if (CollectionUtils.isEmpty(posts)) {
            return Collections.emptyList();
        }

        if (currentUserId == null) {
            return posts.stream()
                    .map(post -> toPostSummaryView(post, false, false))
                    .toList();
        }

        List<Integer> postIds = posts.stream()
                .map(Post::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<Integer> authorIds = posts.stream()
                .map(Post::getAuthorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Set<Integer> likedPostIds = postIds.isEmpty()
                ? Collections.emptySet()
                : new HashSet<>(postLikeRepo.findLikedPostIds(currentUserId, postIds));

        // 同步批量查询的点赞关系到Redis缓存
        if (!likedPostIds.isEmpty()) {
            try {
                for (Integer postId : likedPostIds) {
                    String likesKey = "post:likes:" + postId;
                    redisTemplate.opsForSet().add(likesKey, currentUserId.toString());
                }
                log.debug("批量查询点赞关系，已同步到Redis: userId={}, count={}", currentUserId, likedPostIds.size());
            } catch (Exception e) {
                log.warn("同步点赞关系到Redis失败: userId={}", currentUserId, e);
            }
        }

        Set<Integer> followedAuthorIds = Collections.emptySet();
        if (!authorIds.isEmpty()) {
            try {
                Result<List<Integer>> result = userClient.getFollowedUserIds(currentUserId, authorIds);
                if (result != null && result.getResult() != null) {
                    followedAuthorIds = new HashSet<>(result.getResult());
                }
            } catch (Exception e) {
                log.warn("调用用户服务获取关注关系失败: userId={}, authorIds={}", currentUserId, authorIds, e);
            }
        }
        
        final Set<Integer> finalFollowedAuthorIds = followedAuthorIds;

        return posts.stream()
                .map(post -> {
                    boolean liked = post.getId() != null && likedPostIds.contains(post.getId());
                    boolean following = post.getAuthorId() != null && finalFollowedAuthorIds.contains(post.getAuthorId());
                    return toPostSummaryView(post, liked, following);
                })
                .toList();
    }

    private PostSummaryView toPostSummaryView(Post post, boolean liked, boolean following) {
        if (post == null) {
            return null;
        }
        List<String> images = Optional.ofNullable(post.getImages()).orElse(Collections.emptyList());
        String contentPreview = generatePreview(post.getContent());

        int viewCount = getMetricFromRedis(post.getId(), "views", post.getViewCount());
        int likeCount = getMetricFromRedis(post.getId(), "likes", post.getLikeCount());
        int commentCount = getMetricFromRedis(post.getId(), "comments", post.getCommentCount());

        return PostSummaryView.builder()
                .id(post.getId() != null ? String.valueOf(post.getId()) : null)
                .title(post.getTitle())
                .subtitle(post.getSubtitle())
                .summary(contentPreview)
                .contentPreview(contentPreview)
                .pinned(Boolean.TRUE.equals(post.getPinned()))
                .images(images)
                .thumbnail(CollectionUtils.isEmpty(images) ? null : images.get(0))
                .authorId(post.getAuthorId() != null ? String.valueOf(post.getAuthorId()) : null)
                .authorName(post.getAuthorName())
                .categoryId(post.getCategoryId() != null ? String.valueOf(post.getCategoryId()) : null)
                .categoryName(post.getCategoryName())
                .createdAt(post.getCreatedAt())
                .commentCount(commentCount)
                .viewCount(viewCount)
                .likeCount(likeCount)
                .liked(liked)
                .following(following)
                .build();
    }

    private AdminPostSummary toAdminPostSummary(Post post) {
        if (post == null) {
            return null;
        }
        AdminPostSummary.AdminPostAuthor author = null;
        if (post.getAuthorId() != null || StringUtils.hasText(post.getAuthorName()) || StringUtils.hasText(post.getAuthorAvatar())) {
            author = new AdminPostSummary.AdminPostAuthor(
                    post.getAuthorId() != null ? String.valueOf(post.getAuthorId()) : null,
                    post.getAuthorName(),
                    post.getAuthorAvatar()
            );
        }
        AdminPostSummary.AdminPostCategory category = null;
        if (post.getCategoryId() != null || StringUtils.hasText(post.getCategoryName())) {
            category = new AdminPostSummary.AdminPostCategory(
                    post.getCategoryId() != null ? String.valueOf(post.getCategoryId()) : null,
                    post.getCategoryName()
            );
        }
        String submittedAt = toIso(post.getCreatedAt());
        return new AdminPostSummary(
                post.getId() != null ? String.valueOf(post.getId()) : null,
                post.getTitle(),
                author,
                formatAdminStatus(post.getStatus()),
                category,
                submittedAt,
                Collections.emptyList(),
                post
        );
    }

    private String formatAdminStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "Pending";
        }
        return switch (status.trim().toLowerCase()) {
            case "pending" -> "Pending";
            case "approved" -> "Approved";
            case "rejected" -> "Rejected";
            default -> status;
        };
    }

    private String toIso(LocalDateTime time) {
        return time != null ? time.atOffset(ZoneOffset.UTC).toString() : null;
    }

    private String normalizeModerationStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim().toLowerCase()) {
            case "pending" -> "pending";
            case "approved" -> "approved";
            case "rejected" -> "rejected";
            default -> status.trim();
        };
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = "posts:detail", allEntries = true),
            @CacheEvict(cacheNames = "posts:list", allEntries = true)
    })
    protected AdminPostSummary moderatePostStatus(Integer postId, String targetStatus) {
        if (postId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Post id is required");
        }
        Post existing = baseMapper.selectByIdWithAuthor(postId);
        if (existing == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Post not found");
        }
        String normalizedStatus = normalizeModerationStatus(targetStatus);
        if (!StringUtils.hasText(normalizedStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid target status");
        }
        LocalDateTime now = LocalDateTime.now();
        boolean updated = this.update(
                Wrappers.<Post>lambdaUpdate()
                        .eq(Post::getId, postId)
                        .set(Post::getStatus, normalizedStatus)
                        .set(Post::getUpdatedAt, now)
        );
        if (!updated) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update post status");
        }
        Post refreshed = baseMapper.selectByIdWithAuthor(postId);
        return toAdminPostSummary(refreshed != null ? refreshed : existing);
    }

    private String determineStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "pending";
        }
        return switch (status.toLowerCase()) {
            case "draft" -> "draft";
            case "approved" -> "approved";
            default -> "pending";
        };
    }

    private String generatePreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String plain = content.replaceAll("<[^>]*>", "");
        return plain.length() > 100 ? plain.substring(0, 100) + "..." : plain;
    }

    /**
     * 从 Redis Hash 获取指标值（增量模式）
     * 
     * Redis Hash结构：post:metrics:{postId} -> {views: +5, likes: +2, comments: +3}
     * 策略：数据库值（基准） + Redis增量 = 最终显示值
     * 
     * @param postId 帖子ID
     * @param field 字段名（views/likes/comments）
     * @param dbValue 数据库中的基准值
     * @return 指标最终值（基准值 + 增量）
     */
    private int getMetricFromRedis(Integer postId, String field, Integer dbValue) {
        try {
            String metricsKey = "post:metrics:" + postId;
            Object deltaObj = redisTemplate.opsForHash().get(metricsKey, field);

            // 数据库基准值
            int baseValue = Optional.ofNullable(dbValue).orElse(0);
            
            if (deltaObj != null) {
                // Redis中有增量，加到基准值上
                int delta = Integer.parseInt(deltaObj.toString());
                int finalValue = Math.max(0, baseValue + delta);
                log.debug("计算指标值: postId={}, field={}, base={}, delta={}, final={}", 
                         postId, field, baseValue, delta, finalValue);
                return finalValue;
            } else {
                // Redis中没有增量，直接返回数据库值
                return baseValue;
            }
        } catch (Exception e) {
            log.warn("从 Redis 读取计数失败, postId={}, field={}, 使用数据库值", postId, field, e);
            return Optional.ofNullable(dbValue).orElse(0);
        }
    }

    private List<TrendingPostView> getTrendingFromRedis(int limit) {
        try {
            String trendingKey = "post:trending";
            Set<Object> topPostIds = redisTemplate.opsForZSet().reverseRange(trendingKey, 0, limit - 1);

            if (CollectionUtils.isEmpty(topPostIds)) {
                return Collections.emptyList();
            }

            List<Integer> postIds = topPostIds.stream()
                    .map(id -> Integer.parseInt(id.toString()))
                    .toList();

            List<Post> posts = listByIds(postIds);
            if (CollectionUtils.isEmpty(posts)) {
                return Collections.emptyList();
            }

            Map<Integer, Post> postMap = posts.stream()
                    .collect(Collectors.toMap(Post::getId, p -> p));

            return postIds.stream()
                    .map(postMap::get)
                    .filter(Objects::nonNull)
                    .map(post -> {
                        Double score = redisTemplate.opsForZSet().score("post:trending", post.getId().toString());
                        int heatScore = score != null ? score.intValue() : 0;
                        return new TrendingPostView(
                                String.valueOf(post.getId()),
                                post.getTitle(),
                                heatScore
                        );
                    })
                    .toList();

        } catch (Exception e) {
            log.error("从 Redis 读取热门帖子失败", e);
            return Collections.emptyList();
        }
    }

    private void syncTrendingPostsToRedis(List<Post> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }

        try {
            String trendingKey = "post:trending";

            for (Post post : posts) {
                int viewCount = getMetricFromRedis(post.getId(), "views", post.getViewCount());
                int likeCount = getMetricFromRedis(post.getId(), "likes", post.getLikeCount());
                int commentCount = getMetricFromRedis(post.getId(), "comments", post.getCommentCount());

                double heatScore = calculateHeatScore(viewCount, likeCount, commentCount);
                redisTemplate.opsForZSet().add(trendingKey, post.getId().toString(), heatScore);
            }

            log.debug("同步 {} 个帖子到 Redis trending 榜单", posts.size());

        } catch (Exception e) {
            log.error("同步帖子到 Redis 失败", e);
        }
    }

    private double calculateHeatScore(int viewCount, int likeCount, int commentCount) {
        return viewCount * 0.1 + likeCount * 2.0 + commentCount * 5.0;
    }
}
