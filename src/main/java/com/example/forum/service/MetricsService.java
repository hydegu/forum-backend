package com.example.forum.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 记录自定义业务指标的服务
 *
 * 此服务提供便捷的方法来记录各种业务事件，
 * 这些事件将通过 Prometheus 指标端点暴露。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // 用户指标
    public void recordUserRegistration() {
        Counter.builder("forum.user.registration")
                .description("用户注册总数")
                .register(meterRegistry)
                .increment();
        log.debug("已记录用户注册指标");
    }

    // 文章指标
    public void recordPostCreation() {
        Counter.builder("forum.post.created")
                .description("文章创建总数")
                .register(meterRegistry)
                .increment();
        log.debug("已记录文章创建指标");
    }

    public void recordPostView(Long postId) {
        Counter.builder("forum.post.views")
                .description("文章浏览总数")
                .tag("postId", String.valueOf(postId))
                .register(meterRegistry)
                .increment();
    }

    public void recordPostLike(Long postId) {
        Counter.builder("forum.post.likes")
                .description("文章点赞总数")
                .tag("postId", String.valueOf(postId))
                .register(meterRegistry)
                .increment();
    }

    public void recordPostUpdate() {
        Counter.builder("forum.post.updated")
                .description("文章更新总数")
                .register(meterRegistry)
                .increment();
    }

    public void recordPostDeletion() {
        Counter.builder("forum.post.deleted")
                .description("文章删除总数")
                .register(meterRegistry)
                .increment();
    }

    // 评论指标
    public void recordCommentCreation() {
        Counter.builder("forum.comment.created")
                .description("评论创建总数")
                .register(meterRegistry)
                .increment();
        log.debug("已记录评论创建指标");
    }

    public void recordCommentDeletion() {
        Counter.builder("forum.comment.deleted")
                .description("评论删除总数")
                .register(meterRegistry)
                .increment();
    }

    // 认证指标
    public void recordLoginSuccess(String username) {
        Counter.builder("forum.auth.login.success")
                .description("登录成功总数")
                .tag("username", username)
                .register(meterRegistry)
                .increment();
        log.debug("已记录用户登录成功: {}", username);
    }

    public void recordLoginFailure(String username) {
        Counter.builder("forum.auth.login.failure")
                .description("登录失败总数")
                .tag("username", username)
                .register(meterRegistry)
                .increment();
        log.debug("已记录用户登录失败: {}", username);
    }

    // 缓存指标
    public void recordCacheHit(String cacheName) {
        Counter.builder("forum.cache.hit")
                .description("缓存命中总数")
                .tag("cache", cacheName)
                .register(meterRegistry)
                .increment();
    }

    public void recordCacheMiss(String cacheName) {
        Counter.builder("forum.cache.miss")
                .description("缓存未命中总数")
                .tag("cache", cacheName)
                .register(meterRegistry)
                .increment();
    }

    // 数据库指标
    public Timer.Sample startDatabaseQueryTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopDatabaseQueryTimer(Timer.Sample sample, String queryType) {
        sample.stop(Timer.builder("forum.database.query")
                .description("数据库查询执行时间")
                .tag("type", queryType)
                .register(meterRegistry));
    }

    // Redis 指标
    public Timer.Sample startRedisOperationTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopRedisOperationTimer(Timer.Sample sample, String operation) {
        sample.stop(Timer.builder("forum.redis.operation")
                .description("Redis 操作执行时间")
                .tag("operation", operation)
                .register(meterRegistry));
    }

    // 邮件指标
    public void recordEmailSent(String emailType) {
        Counter.builder("forum.email.sent")
                .description("邮件发送总数")
                .tag("type", emailType)
                .register(meterRegistry)
                .increment();
        log.debug("已记录邮件发送: {}", emailType);
    }

    public void recordEmailFailure(String emailType) {
        Counter.builder("forum.email.failure")
                .description("邮件发送失败总数")
                .tag("type", emailType)
                .register(meterRegistry)
                .increment();
        log.warn("已记录邮件发送失败: {}", emailType);
    }

    // API 端点指标（用于特定业务逻辑跟踪的自定义方法）
    public void recordApiCall(String endpoint, String method, int statusCode) {
        Counter.builder("forum.api.calls")
                .description("API 调用总数")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", String.valueOf(statusCode))
                .register(meterRegistry)
                .increment();
    }

    // 业务操作计时器
    public Timer.Sample startBusinessOperationTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopBusinessOperationTimer(Timer.Sample sample, String operation) {
        sample.stop(Timer.builder("forum.business.operation")
                .description("业务操作执行时间")
                .tag("operation", operation)
                .register(meterRegistry));
    }
}