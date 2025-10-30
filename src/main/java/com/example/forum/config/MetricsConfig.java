package com.example.forum.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Prometheus 监控自定义业务指标配置
 *
 * 此配置定义了以下自定义指标：
 * - 用户注册
 * - 文章操作（创建、浏览、点赞）
 * - 评论操作（创建、删除）
 * - 缓存操作（命中、未命中）
 * - 认证操作（登录成功/失败）
 */
@Slf4j
@Configuration
public class MetricsConfig {

    /**
     * 用户注册事件计数器
     */
    @Bean
    public Counter userRegistrationCounter(MeterRegistry registry) {
        return Counter.builder("forum.user.registration")
                .description("用户注册总数")
                .tag("type", "new_user")
                .register(registry);
    }

    /**
     * 文章创建事件计数器
     */
    @Bean
    public Counter postCreationCounter(MeterRegistry registry) {
        return Counter.builder("forum.post.created")
                .description("文章创建总数")
                .tag("type", "post")
                .register(registry);
    }

    /**
     * 文章浏览事件计数器
     */
    @Bean
    public Counter postViewCounter(MeterRegistry registry) {
        return Counter.builder("forum.post.views")
                .description("文章浏览总数")
                .tag("type", "view")
                .register(registry);
    }

    /**
     * 文章点赞事件计数器
     */
    @Bean
    public Counter postLikeCounter(MeterRegistry registry) {
        return Counter.builder("forum.post.likes")
                .description("文章点赞总数")
                .tag("type", "like")
                .register(registry);
    }

    /**
     * 评论创建事件计数器
     */
    @Bean
    public Counter commentCreationCounter(MeterRegistry registry) {
        return Counter.builder("forum.comment.created")
                .description("评论创建总数")
                .tag("type", "comment")
                .register(registry);
    }

    /**
     * 评论删除事件计数器
     */
    @Bean
    public Counter commentDeletionCounter(MeterRegistry registry) {
        return Counter.builder("forum.comment.deleted")
                .description("评论删除总数")
                .tag("type", "comment")
                .register(registry);
    }

    /**
     * 登录成功事件计数器
     */
    @Bean
    public Counter loginSuccessCounter(MeterRegistry registry) {
        return Counter.builder("forum.auth.login.success")
                .description("登录成功总数")
                .tag("type", "authentication")
                .register(registry);
    }

    /**
     * 登录失败事件计数器
     */
    @Bean
    public Counter loginFailureCounter(MeterRegistry registry) {
        return Counter.builder("forum.auth.login.failure")
                .description("登录失败总数")
                .tag("type", "authentication")
                .register(registry);
    }

    /**
     * 缓存命中事件计数器
     */
    @Bean
    public Counter cacheHitCounter(MeterRegistry registry) {
        return Counter.builder("forum.cache.hit")
                .description("缓存命中总数")
                .tag("type", "cache")
                .register(registry);
    }

    /**
     * 缓存未命中事件计数器
     */
    @Bean
    public Counter cacheMissCounter(MeterRegistry registry) {
        return Counter.builder("forum.cache.miss")
                .description("缓存未命中总数")
                .tag("type", "cache")
                .register(registry);
    }

    /**
     * 数据库查询执行时间计时器
     */
    @Bean
    public Timer databaseQueryTimer(MeterRegistry registry) {
        return Timer.builder("forum.database.query")
                .description("数据库查询执行时间")
                .tag("type", "database")
                .register(registry);
    }

    /**
     * Redis 操作计时器
     */
    @Bean
    public Timer redisOperationTimer(MeterRegistry registry) {
        return Timer.builder("forum.redis.operation")
                .description("Redis 操作执行时间")
                .tag("type", "cache")
                .register(registry);
    }

    /**
     * 邮件发送事件计数器
     */
    @Bean
    public Counter emailSentCounter(MeterRegistry registry) {
        return Counter.builder("forum.email.sent")
                .description("邮件发送总数")
                .tag("type", "notification")
                .register(registry);
    }

    /**
     * 邮件发送失败事件计数器
     */
    @Bean
    public Counter emailFailureCounter(MeterRegistry registry) {
        return Counter.builder("forum.email.failure")
                .description("邮件发送失败总数")
                .tag("type", "notification")
                .register(registry);
    }
}