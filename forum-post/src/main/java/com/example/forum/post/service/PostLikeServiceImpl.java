package com.example.forum.post.service;

import com.example.forum.post.entity.Post;
import com.example.forum.post.entity.PostLike;
import com.example.forum.post.repo.PostLikeRepo;
import com.example.forum.post.repo.PostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * 帖子点赞服务实现
 * 使用 Redis 缓存提高性能，并保证与数据库的一致性
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PostLikeServiceImpl implements PostLikeService {

    private final PostLikeRepo postLikeRepo;
    private final PostRepo postRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "posts:detail", key = "#postId")
    public boolean likePost(Integer postId, Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("未登录用户无法点赞");
        }

        Post post = postRepo.selectById(postId);
        if (post == null) {
            throw new NoSuchElementException("帖子不存在");
        }

        // Redis 集合镜像：使用 SET 存储点赞关系
        String likesKey = "post:likes:" + postId;
        Long added = redisTemplate.opsForSet().add(likesKey, userId.toString());

        if (Boolean.TRUE.equals(added != null && added > 0)) {
            // Redis SET 添加成功，说明是新点赞
            // 1. 更新 Redis 点赞计数
            redisTemplate.opsForHash().increment("post:metrics:" + postId, "likes", 1);

            // 2. 数据库操作（强一致性：同步写入）
            try {
                PostLike like = new PostLike()
                        .setPostId(postId)
                        .setUserId(userId)
                        .setCreatedAt(LocalDateTime.now());
                postLikeRepo.insert(like);
                log.debug("点赞成功: postId={}, userId={}", postId, userId);
            } catch (Exception e) {
                // 数据库写入失败，回滚 Redis
                redisTemplate.opsForSet().remove(likesKey, userId.toString());
                redisTemplate.opsForHash().increment("post:metrics:" + postId, "likes", -1);
                log.error("数据库写入点赞失败，已回滚Redis: postId={}, userId={}", postId, userId, e);
                throw e;
            }

            return true;
        } else {
            // Redis SET 添加失败，说明已经点赞过了
            log.debug("用户已点赞过该帖子: postId={}, userId={}", postId, userId);
            return true;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = "posts:detail", key = "#postId")
    public boolean unlikePost(Integer postId, Integer userId) {
        if (userId == null) {
            return false;
        }

        Post post = postRepo.selectById(postId);
        if (post == null) {
            return false;
        }

        // Redis 集合镜像：从 SET 移除点赞关系
        String likesKey = "post:likes:" + postId;
        Long removed = redisTemplate.opsForSet().remove(likesKey, userId.toString());

        if (Boolean.TRUE.equals(removed != null && removed > 0)) {
            // Redis SET 移除成功，说明确实有点赞
            // 1. 更新 Redis 点赞计数
            redisTemplate.opsForHash().increment("post:metrics:" + postId, "likes", -1);

            // 2. 数据库操作（强一致性：同步删除）
            try {
                PostLike existing = postLikeRepo.findByPostAndUser(postId, userId);
                if (existing != null) {
                    postLikeRepo.deleteById(existing.getId());
                    log.debug("取消点赞成功: postId={}, userId={}", postId, userId);
                }
            } catch (Exception e) {
                // 数据库删除失败，回滚 Redis
                redisTemplate.opsForSet().add(likesKey, userId.toString());
                redisTemplate.opsForHash().increment("post:metrics:" + postId, "likes", 1);
                log.error("数据库删除点赞失败，已回滚Redis: postId={}, userId={}", postId, userId, e);
                throw e;
            }

            return true;
        } else {
            // Redis SET 移除失败，说明本就没有点赞
            log.debug("用户未点赞该帖子: postId={}, userId={}", postId, userId);
            return false;
        }
    }

    @Override
    public boolean isPostLikedByUser(Integer postId, Integer userId) {
        if (userId == null) {
            return false;
        }

        // 优先从 Redis SET 判断是否点赞
        String likesKey = "post:likes:" + postId;
        try {
            Boolean isMember = redisTemplate.opsForSet().isMember(likesKey, userId.toString());
            if (Boolean.TRUE.equals(isMember)) {
                // Redis缓存命中，直接返回
                return true;
            }

            // Redis缓存未命中，回源数据库查询
            PostLike dbLike = postLikeRepo.findByPostAndUser(postId, userId);
            if (dbLike != null) {
                // 数据库中有点赞记录，同步到Redis
                redisTemplate.opsForSet().add(likesKey, userId.toString());
                log.debug("同步点赞关系到Redis: postId={}, userId={}", postId, userId);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.warn("Redis查询失败，回退到数据库: postId={}, userId={}", postId, userId, e);
            return postLikeRepo.findByPostAndUser(postId, userId) != null;
        }
    }
}
