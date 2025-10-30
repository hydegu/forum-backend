package com.example.forum.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.forum.dto.PostCreateRequest;
import com.example.forum.entity.Author;
import com.example.forum.entity.Post;
import com.example.forum.entity.PostLike;
import com.example.forum.entity.UserFollow;
import com.example.forum.exception.ApiException;
import com.example.forum.repo.PostLikeRepo;
import com.example.forum.repo.PostRepo;
import com.example.forum.repo.UserFollowRepo;
import com.example.forum.repo.UserRepo;
import com.example.forum.vo.AdminPostDetailView;
import com.example.forum.vo.AdminPostSummary;
import com.example.forum.vo.CategoryResponse;
import com.example.forum.vo.CommentTreeNode;
import com.example.forum.vo.PageResponse;
import com.example.forum.vo.PostDetailView;
import com.example.forum.vo.PostListResponse;
import com.example.forum.vo.PostSummaryView;
import com.example.forum.vo.TrendingPostView;
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
    private final UserRepo userRepo;
    private final UserFollowRepo userFollowRepo;
    private final CommentService commentService;
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
    // @Cacheable 已移除：为了确保 metrics（点赞、评论、浏览量）能够实时更新
    // 虽然移除了整体缓存，但 metrics 从 Redis 读取，性能仍然很好
    public PostListResponse pageWithSummary(Integer current, Integer pageSize, Wrapper<Post> wrapper,
                                            String status, String q, Integer categoryId, Integer currentUserId) {
        // 使用轻量级查询（不包含content字段），优化网络传输
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
        // 使用轻量级查询（管理员列表不需要完整content）
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
        // 先尝试从 Redis Sorted Set 读取热门帖子
        List<TrendingPostView> cachedTrending = getTrendingFromRedis(limit);

        if (!cachedTrending.isEmpty()) {
            log.debug("从 Redis 缓存返回热门帖子，数量: {}", cachedTrending.size());
            return cachedTrending;
        }

        // 缓存为空，从数据库查询
        log.debug("Redis 缓存为空，从数据库查询热门帖子");
        LambdaQueryWrapper<Post> wrapper = Wrappers.lambdaQuery(Post.class)
                .eq(Post::getStatus, "approved")
                .orderByDesc(Post::getHeat)
                .last("LIMIT " + limit);
        List<Post> posts = this.list(wrapper);

        // 同步到 Redis
        syncTrendingPostsToRedis(posts);

        // 转换并返回
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
        // 浏览量递增（不受缓存影响，每次访问都递增）
        incrementViewCount(postId);

        // 获取帖子详情（可能从缓存返回）
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

        // 从 Redis 获取实时计数（合并数据库和 Redis 的值）
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
        // 使用轻量级查询（我的帖子列表不需要完整content）
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
        if (authorId != null && userRepo.selectById(authorId) == null) {
            throw new NoSuchElementException("用户不存在");
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

    /**
     * 递增帖子浏览量（使用 Redis）
     */
    private void incrementViewCount(Integer postId) {
        String metricsKey = "post:metrics:" + postId;
        redisTemplate.opsForHash().increment(metricsKey, "views", 1);
        log.debug("递增帖子浏览量: postId={}", postId);
    }

    private boolean isAuthorFollowedBy(Integer authorId, Integer followerId) {
        if (authorId == null || followerId == null) {
            return false;
        }
        UserFollow relation = userFollowRepo.findRelation(followerId, authorId);
        return relation != null;
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
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        List<Integer> authorIds = posts.stream()
                .map(Post::getAuthorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        java.util.Set<Integer> likedPostIds = postIds.isEmpty()
                ? java.util.Collections.emptySet()
                : new java.util.HashSet<>(postLikeRepo.findLikedPostIds(currentUserId, postIds));
        java.util.Set<Integer> followedAuthorIds = authorIds.isEmpty()
                ? java.util.Collections.emptySet()
                : new java.util.HashSet<>(userFollowRepo.findFolloweeIds(currentUserId, authorIds));

        return posts.stream()
                .map(post -> {
                    boolean liked = post.getId() != null && likedPostIds.contains(post.getId());
                    boolean following = post.getAuthorId() != null && followedAuthorIds.contains(post.getAuthorId());
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

        // 从 Redis 获取实时计数（合并数据库和 Redis 的值）
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
        List<CommentTreeNode> comments = includeComments
                ? commentService.pageComments(postId, 1, 50).getRecords()
                : null;
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
     * 从 Redis 获取实时计数值
     *
     * 注意：Redis 中存储的是增量值（delta），需要与数据库值相加得到实时值
     * - 数据库值：最近一次同步时的累计值
     * - Redis 值：自上次同步后的增量
     * - 实时值：数据库值 + Redis 增量
     *
     * @param postId 帖子ID
     * @param field 计数字段名（views/likes/comments）
     * @param dbValue 数据库中的值（最近一次同步时的累计值）
     * @return 实时计数值（数据库值 + Redis 增量）
     */
    private int getMetricFromRedis(Integer postId, String field, Integer dbValue) {
        try {
            String metricsKey = "post:metrics:" + postId;
            Object value = redisTemplate.opsForHash().get(metricsKey, field);

            int baseValue = Optional.ofNullable(dbValue).orElse(0);

            if (value != null) {
                // Redis 中有增量，返回 数据库值 + Redis 增量
                int delta = Integer.parseInt(value.toString());
                return baseValue + delta;
            } else {
                // Redis 中无增量，返回数据库值
                return baseValue;
            }
        } catch (Exception e) {
            log.warn("从 Redis 读取计数失败, postId={}, field={}, 使用数据库值", postId, field, e);
            return Optional.ofNullable(dbValue).orElse(0);
        }
    }

    /**
     * 计算帖子热度分值
     * 公式: 浏览量 * 0.1 + 点赞数 * 2 + 评论数 * 5
     *
     * @param viewCount 浏览量
     * @param likeCount 点赞数
     * @param commentCount 评论数
     * @return 热度分值
     */
    public double calculateHeatScore(int viewCount, int likeCount, int commentCount) {
        return viewCount * 0.1 + likeCount * 2.0 + commentCount * 5.0;
    }

    /**
     * 从 Redis Sorted Set 读取热门帖子
     *
     * @param limit 返回的帖子数量
     * @return 热门帖子列表
     */
    private List<TrendingPostView> getTrendingFromRedis(int limit) {
        try {
            String trendingKey = "post:trending";
            // 从 Redis Sorted Set 获取 top N 热门帖子（按分值降序）
            Set<Object> topPostIds = redisTemplate.opsForZSet().reverseRange(trendingKey, 0, limit - 1);

            if (CollectionUtils.isEmpty(topPostIds)) {
                return Collections.emptyList();
            }

            // 批量查询帖子信息
            List<Integer> postIds = topPostIds.stream()
                    .map(id -> Integer.parseInt(id.toString()))
                    .toList();

            List<Post> posts = listByIds(postIds);
            if (CollectionUtils.isEmpty(posts)) {
                return Collections.emptyList();
            }

            // 按照 Redis 中的顺序排序并转换
            Map<Integer, Post> postMap = posts.stream()
                    .collect(Collectors.toMap(Post::getId, p -> p));

            return postIds.stream()
                    .map(postMap::get)
                    .filter(java.util.Objects::nonNull)
                    .map(post -> {
                        // 从 Redis 获取实时热度分值
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

    /**
     * 同步帖子到 Redis Sorted Set
     *
     * @param posts 帖子列表
     */
    private void syncTrendingPostsToRedis(List<Post> posts) {
        if (CollectionUtils.isEmpty(posts)) {
            return;
        }

        try {
            String trendingKey = "post:trending";

            // 批量添加到 Redis Sorted Set
            for (Post post : posts) {
                // 从 Redis 获取实时计数，如果没有则使用数据库值
                int viewCount = getMetricFromRedis(post.getId(), "views", post.getViewCount());
                int likeCount = getMetricFromRedis(post.getId(), "likes", post.getLikeCount());
                int commentCount = getMetricFromRedis(post.getId(), "comments", post.getCommentCount());

                double heatScore = calculateHeatScore(viewCount, likeCount, commentCount);
                //存入到有序集合ZSet中，参数分别为key value score,score为排序依据
                redisTemplate.opsForZSet().add(trendingKey, post.getId().toString(), heatScore);
            }

            log.debug("同步 {} 个帖子到 Redis trending 榜单", posts.size());

        } catch (Exception e) {
            log.error("同步帖子到 Redis 失败", e);
        }
    }

    // ================================================================================================
    // Redis 集合镜像 (SET) 数据回写策略说明
    // ================================================================================================
    /**
     * Redis SET 镜像数据同步说明
     *
     * 当前实现策略：强一致性（同步双写）
     * --------------------------------------
     * 1. 点赞/取消点赞时，同时写入 Redis SET 和数据库
     * 2. Redis 作为读优化层，数据库作为持久化层
     * 3. 写入失败时回滚 Redis，保证数据一致性
     *
     * 涉及的 Redis Key：
     * - post:likes:{postId}  -> SET，存储点赞该帖子的所有 userId
     * - post:metrics:{postId} -> HASH，存储帖子的点赞计数
     *
     * 如果需要实现最终一致性（异步回写），可采用以下方案：
     * --------------------------------------
     *
     * 方案A：定时任务对账（推荐用于一致性要求不高的场景）
     * -------------------------------------------------------
     * 定时任务（如每小时执行）：
     * 1. 扫描 Redis 中的 post:likes:* keys
     * 2. 对比数据库中的点赞记录
     * 3. 同步差异数据到数据库
     *
     * 示例代码：
     * <pre>{@code
     * @Scheduled(cron = "0 0 * * * ?") // 每小时执行
     * public void syncLikesFromRedisToDb() {
     *     Set<String> keys = redisTemplate.keys("post:likes:*");
     *     for (String key : keys) {
     *         Integer postId = extractPostIdFromKey(key);
     *         Set<Object> redisUserIds = redisTemplate.opsForSet().members(key);
     *
     *         // 查询数据库中的点赞记录
     *         List<PostLike> dbLikes = postLikeRepo.findByPostId(postId);
     *         Set<Integer> dbUserIds = dbLikes.stream()
     *             .map(PostLike::getUserId)
     *             .collect(Collectors.toSet());
     *
     *         // 找出 Redis 有但数据库没有的（需要插入）
     *         for (Object userId : redisUserIds) {
     *             Integer uid = Integer.parseInt(userId.toString());
     *             if (!dbUserIds.contains(uid)) {
     *                 PostLike like = new PostLike()
     *                     .setPostId(postId)
     *                     .setUserId(uid)
     *                     .setCreatedAt(LocalDateTime.now());
     *                 postLikeRepo.insert(like);
     *             }
     *         }
     *
     *         // 找出数据库有但 Redis 没有的（需要删除或同步）
     *         for (Integer dbUserId : dbUserIds) {
     *             if (!redisUserIds.contains(dbUserId.toString())) {
     *                 // 以数据库为准，同步到 Redis
     *                 redisTemplate.opsForSet().add(key, dbUserId.toString());
     *             }
     *         }
     *     }
     * }
     * }</pre>
     *
     * 方案B：启动时全量同步（适合重启后恢复缓存）
     * -------------------------------------------------------
     * 应用启动时加载数据库点赞数据到 Redis：
     *
     * <pre>{@code
     * @PostConstruct
     * public void warmUpLikesCache() {
     *     // 1. 清空现有缓存
     *     Set<String> keys = redisTemplate.keys("post:likes:*");
     *     if (!CollectionUtils.isEmpty(keys)) {
     *         redisTemplate.delete(keys);
     *     }
     *
     *     // 2. 从数据库加载所有点赞记录
     *     List<PostLike> allLikes = postLikeRepo.selectList(null);
     *
     *     // 3. 按 postId 分组
     *     Map<Integer, List<Integer>> postLikesMap = allLikes.stream()
     *         .collect(Collectors.grouping(
     *             PostLike::getPostId,
     *             Collectors.mapping(PostLike::getUserId, Collectors.toList())
     *         ));
     *
     *     // 4. 批量写入 Redis
     *     for (Map.Entry<Integer, List<Integer>> entry : postLikesMap.entrySet()) {
     *         String key = "post:likes:" + entry.getKey();
     *         String[] userIds = entry.getValue().stream()
     *             .map(String::valueOf)
     *             .toArray(String[]::new);
     *         redisTemplate.opsForSet().add(key, userIds);
     *     }
     *
     *     log.info("预热点赞缓存完成，共 {} 个帖子", postLikesMap.size());
     * }
     * }</pre>
     *
     * 方案C：消息队列异步回写（高并发场景）
     * -------------------------------------------------------
     * 1. 点赞/取消点赞时只写 Redis
     * 2. 发送消息到队列（如 RabbitMQ/Kafka）
     * 3. 消费者异步写入数据库
     * 4. 失败重试机制保证最终一致性
     *
     * 当前项目建议：
     * --------------
     * 1. 保持当前的强一致性实现（已足够满足需求）
     * 2. 如遇到性能瓶颈，优先考虑方案A（定时对账）
     * 3. 启动时可使用方案B预热缓存
     * 4. 高并发场景才考虑方案C（消息队列）
     */
}

