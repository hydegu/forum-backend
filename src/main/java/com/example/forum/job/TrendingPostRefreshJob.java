package com.example.forum.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.forum.entity.Post;
import com.example.forum.repo.PostRepo;
import com.example.forum.service.PostServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 热门帖子排行榜刷新定时任务
 * 定期计算帖子热度并更新 Redis Sorted Set
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrendingPostRefreshJob {

    private final PostRepo postRepo;
    private final RedisTemplate<String, Object> redisTemplate;
    private final PostServiceImpl postService;

    private static final String TRENDING_KEY = "post:trending";
    private static final int TOP_N = 100; // 缓存前100个热门帖子

    /**
     * 刷新热门帖子排行榜
     * 默认每10分钟执行一次
     */
    @Scheduled(fixedDelayString = "${forum.trending.refresh-interval:600000}") // 10分钟 = 600000ms
    public void refreshTrendingPosts() {
        long startTime = System.currentTimeMillis();
        log.info("开始刷新热门帖子排行榜...");

        try {
            // 查询近30天内的已审核帖子（避免查询所有数据）
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                    .eq(Post::getStatus, "approved")
                    .ge(Post::getCreatedAt, thirtyDaysAgo)
                    .orderByDesc(Post::getCreatedAt);

            List<Post> recentPosts = postRepo.selectList(wrapper);

            if (CollectionUtils.isEmpty(recentPosts)) {
                log.warn("没有找到符合条件的帖子，跳过刷新");
                return;
            }

            int updatedCount = updateTrendingScores(recentPosts);

            // 只保留前 TOP_N 个热门帖子，删除排名靠后的
            trimTrendingList();

            // 设置过期时间（防止 Redis 内存泄漏）
            redisTemplate.expire(TRENDING_KEY, 1, TimeUnit.HOURS);

            long duration = System.currentTimeMillis() - startTime;
            log.info("热门帖子排行榜刷新完成. 处理帖子数: {}, 更新数: {}, 耗时: {}ms",
                    recentPosts.size(), updatedCount, duration);

        } catch (Exception e) {
            log.error("刷新热门帖子排行榜失败", e);
        }
    }

    /**
     * 更新帖子的热度分值
     *
     * @param posts 帖子列表
     * @return 更新的帖子数量
     */
    private int updateTrendingScores(List<Post> posts) {
        int count = 0;

        for (Post post : posts) {
            try {
                // 从 Redis 获取实时计数
                int viewCount = getMetricFromRedis(post.getId(), "views", post.getViewCount());
                int likeCount = getMetricFromRedis(post.getId(), "likes", post.getLikeCount());
                int commentCount = getMetricFromRedis(post.getId(), "comments", post.getCommentCount());

                // 计算热度分值
                double heatScore = postService.calculateHeatScore(viewCount, likeCount, commentCount);

                // 更新到 Redis Sorted Set
                redisTemplate.opsForZSet().add(TRENDING_KEY, post.getId().toString(), heatScore);

                count++;

            } catch (Exception e) {
                log.error("更新帖子 {} 的热度分值失败", post.getId(), e);
            }
        }

        return count;
    }

    /**
     * 只保留前 TOP_N 个热门帖子
     */
    private void trimTrendingList() {
        try {
            Long totalCount = redisTemplate.opsForZSet().size(TRENDING_KEY);
            if (totalCount != null && totalCount > TOP_N) {
                // 删除排名靠后的帖子（保留分数最高的TOP_N个）
                long removeCount = redisTemplate.opsForZSet().removeRange(TRENDING_KEY, 0, totalCount - TOP_N - 1);
                log.debug("删除了 {} 个排名靠后的帖子", removeCount);
            }
        } catch (Exception e) {
            log.error("清理热门帖子列表失败", e);
        }
    }

    /**
     * 从 Redis 获取计数器值
     */
    private int getMetricFromRedis(Integer postId, String field, Integer dbValue) {
        try {
            String metricsKey = "post:metrics:" + postId;
            Object value = redisTemplate.opsForHash().get(metricsKey, field);

            if (value != null) {
                return Integer.parseInt(value.toString());
            } else {
                return dbValue != null ? dbValue : 0;
            }
        } catch (Exception e) {
            log.warn("从 Redis 读取计数失败, postId={}, field={}", postId, field);
            return dbValue != null ? dbValue : 0;
        }
    }
}