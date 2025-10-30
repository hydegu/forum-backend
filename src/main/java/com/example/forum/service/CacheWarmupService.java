package com.example.forum.service;

import com.example.forum.entity.Post;
import com.example.forum.repo.PostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热和预加载服务
 *
 * 此服务功能：
 * - 在应用启动时预加载常用数据
 * - 预热热门文章缓存
 * - 监控缓存健康状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheWarmupService {

    private final PostRepo postRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;
    private final MetricsService metricsService;

    /**
     * 应用启动时预热缓存
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        log.info("开始缓存预热...");
        long startTime = System.currentTimeMillis();

        try {
            // 预热热门文章
            warmupTrendingPosts();

            // 预热最新文章
            warmupRecentPosts();

            long duration = System.currentTimeMillis() - startTime;
            log.info("缓存预热完成，耗时: {}ms", duration);
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }

    /**
     * 预加载热门文章到缓存
     */
    private void warmupTrendingPosts() {
        try {
            log.info("正在预热热门文章...");

            // 加载浏览量前 20 的文章
            List<Post> trendingPosts = postRepo.selectList(null)
                    .stream()
                    .filter(post -> "approved".equals(post.getStatus()))
                    .sorted((p1, p2) -> Long.compare(p2.getViewCount(), p1.getViewCount()))
                    .limit(20)
                    .toList();

            for (Post post : trendingPosts) {
                String cacheKey = "post:detail:" + post.getId();
                redisTemplate.opsForValue().set(cacheKey, post, 5, TimeUnit.MINUTES);
                log.debug("已缓存热门文章: {}", post.getId());
            }

            log.info("已预热 {} 篇热门文章", trendingPosts.size());
        } catch (Exception e) {
            log.error("预热热门文章失败", e);
        }
    }

    /**
     * 预加载最新文章到缓存
     */
    private void warmupRecentPosts() {
        try {
            log.info("正在预热最新文章...");

            // 加载最新 50 篇已审核文章
            List<Post> recentPosts = postRepo.selectList(null)
                    .stream()
                    .filter(post -> "approved".equals(post.getStatus()))
                    .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                    .limit(50)
                    .toList();

            String cacheKey = "posts:recent";
            redisTemplate.opsForValue().set(cacheKey, recentPosts, 3, TimeUnit.MINUTES);

            log.info("已预热 {} 篇最新文章", recentPosts.size());
        } catch (Exception e) {
            log.error("预热最新文章失败", e);
        }
    }

    /**
     * 监控缓存统计信息
     */
    public void logCacheStatistics() {
        try {
            if (cacheManager != null) {
                cacheManager.getCacheNames().forEach(cacheName -> {
                    var cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        log.info("缓存 '{}' 统计信息已记录", cacheName);
                    }
                });
            }
        } catch (Exception e) {
            log.error("记录缓存统计信息失败", e);
        }
    }

    /**
     * 清除所有缓存（谨慎使用）
     */
    public void clearAllCaches() {
        log.warn("正在清除所有缓存...");
        try {
            if (cacheManager != null) {
                cacheManager.getCacheNames().forEach(cacheName -> {
                    var cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                        log.info("已清除缓存: {}", cacheName);
                    }
                });
            }
        } catch (Exception e) {
            log.error("清除缓存失败", e);
        }
    }

    /**
     * 刷新指定文章的缓存
     */
    public void refreshPostCache(Long postId) {
        try {
            Post post = postRepo.selectById(postId);
            if (post != null) {
                String cacheKey = "post:detail:" + postId;
                redisTemplate.opsForValue().set(cacheKey, post, 5, TimeUnit.MINUTES);
                log.info("已刷新文章缓存: {}", postId);
            }
        } catch (Exception e) {
            log.error("刷新文章缓存失败，文章ID: {}", postId, e);
        }
    }
}