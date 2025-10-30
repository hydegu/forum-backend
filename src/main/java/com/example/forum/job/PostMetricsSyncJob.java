package com.example.forum.job;

import com.example.forum.repo.PostRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 帖子指标同步定时任务
 * 定期将 Redis 中的计数器增量回写到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostMetricsSyncJob {
    private final RedisTemplate<String, Object> redisTemplate;
    private final PostRepo postRepo;

    private static final String METRICS_KEY_PREFIX = "post:metrics:";
    private static final String METRICS_KEY_PATTERN = "post:metrics:*";

    /**
     * 定时将 Redis 中的指标增量刷新到数据库
     * 默认每5分钟执行一次
     */
    @Scheduled(fixedDelayString = "${forum.metrics.flush-interval:300000}") // 5分钟 = 300000ms
    public void flush() {
        long startTime = System.currentTimeMillis();
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        log.info("开始同步帖子指标到数据库...");

        try {
            // 使用 SCAN 替代 KEYS，避免阻塞 Redis
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(METRICS_KEY_PATTERN)
                    .count(100) // 每次扫描100个key
                    .build();

            try (Cursor<byte[]> cursor = redisTemplate.getConnectionFactory()
                    .getConnection()
                    .scan(scanOptions)) {

                while (cursor.hasNext()) {
                    String key = new String(cursor.next());
                    processedCount.incrementAndGet();

                    try {
                        if (syncMetricsForPost(key)) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        log.error("同步帖子指标失败: key={}", key, e);
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("帖子指标同步完成. 处理数: {}, 成功: {}, 失败: {}, 耗时: {}ms",
                    processedCount.get(), successCount.get(), failureCount.get(), duration);

        } catch (Exception e) {
            log.error("帖子指标同步任务执行失败", e);
        }
    }

    /**
     * 同步单个帖子的指标到数据库
     *
     * @param key Redis key (格式: post:metrics:{postId})
     * @return 是否同步成功
     */
    @Transactional(rollbackFor = Exception.class)
    protected boolean syncMetricsForPost(String key) {
        try {
            // 获取帖子ID
            Integer postId = extractPostId(key);
            if (postId == null) {
                log.warn("无效的 metrics key: {}", key);
                return false;
            }

            // 获取所有指标
            Map<Object, Object> metrics = redisTemplate.opsForHash().entries(key);
            if (metrics.isEmpty()) {
                log.debug("帖子 {} 没有待同步的指标", postId);
                return true;
            }

            // 提取各项指标的增量值
            int viewsDelta = getMetricValue(metrics, "views");
            int likesDelta = getMetricValue(metrics, "likes");
            int commentsDelta = getMetricValue(metrics, "comments");

            // 如果所有增量都为0，跳过
            if (viewsDelta == 0 && likesDelta == 0 && commentsDelta == 0) {
                log.debug("帖子 {} 的指标增量均为0，跳过同步", postId);
                return true;
            }

            // 更新数据库
            int updatedRows = postRepo.incrementMetrics(postId, viewsDelta, likesDelta, commentsDelta);
            if (updatedRows == 0) {
                log.warn("帖子 {} 不存在或更新失败", postId);
                return false;
            }

            // 清零 Redis 中的增量（减去已同步的值）
            if (viewsDelta != 0) {
                redisTemplate.opsForHash().increment(key, "views", -viewsDelta);
            }
            if (likesDelta != 0) {
                redisTemplate.opsForHash().increment(key, "likes", -likesDelta);
            }
            if (commentsDelta != 0) {
                redisTemplate.opsForHash().increment(key, "comments", -commentsDelta);
            }

            log.debug("帖子 {} 指标同步成功: views={}, likes={}, comments={}",
                    postId, viewsDelta, likesDelta, commentsDelta);

            return true;

        } catch (DataAccessException e) {
            log.error("数据库操作失败: key={}", key, e);
            throw e; // 触发事务回滚
        } catch (Exception e) {
            log.error("同步指标时发生未知错误: key={}", key, e);
            return false;
        }
    }

    /**
     * 从 Redis key 中提取帖子ID
     *
     * @param key Redis key (格式: post:metrics:{postId})
     * @return 帖子ID，如果格式不正确返回 null
     */
    private Integer extractPostId(String key) {
        try {
            if (key != null && key.startsWith(METRICS_KEY_PREFIX)) {
                String idStr = key.substring(METRICS_KEY_PREFIX.length());
                return Integer.valueOf(idStr);
            }
            return null;
        } catch (NumberFormatException e) {
            log.warn("无法解析帖子ID: key={}", key);
            return null;
        }
    }

    /**
     * 从 metrics map 中获取指定字段的整数值
     *
     * @param metrics Redis hash 数据
     * @param field 字段名
     * @return 字段值，如果不存在或转换失败返回0
     */
    private int getMetricValue(Map<Object, Object> metrics, String field) {
        try {
            Object value = metrics.get(field);
            if (value == null) {
                return 0;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            log.warn("解析指标值失败: field={}, value={}", field, metrics.get(field), e);
            return 0;
        }
    }
}
