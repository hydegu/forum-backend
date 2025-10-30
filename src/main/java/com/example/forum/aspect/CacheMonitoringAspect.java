package com.example.forum.aspect;

import com.example.forum.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 缓存操作监控切面
 *
 * 此切面功能：
 * - 跟踪缓存命中/未命中率
 * - 记录缓存操作指标
 * - 记录缓存性能日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CacheMonitoringAspect {

    private final MetricsService metricsService;
    private final CacheManager cacheManager;

    /**
     * 监控 @Cacheable 方法的缓存命中/未命中情况
     */
    @Around("@annotation(org.springframework.cache.annotation.Cacheable)")
    public Object monitorCacheableMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        // 从 @Cacheable 注解提取缓存名称
        String cacheName = extractCacheName(joinPoint);

        log.debug("监控缓存方法: {}.{}, 缓存: {}", className, methodName, cacheName);

        // 检查缓存中是否存在值
        boolean cacheHit = isCacheHit(cacheName, joinPoint.getArgs());

        if (cacheHit) {
            metricsService.recordCacheHit(cacheName);
            log.debug("缓存命中: {}.{} 在缓存 '{}'", className, methodName, cacheName);
        } else {
            metricsService.recordCacheMiss(cacheName);
            log.debug("缓存未命中: {}.{} 在缓存 '{}'", className, methodName, cacheName);
        }

        // 继续执行方法
        return joinPoint.proceed();
    }

    /**
     * 从 @Cacheable 注解提取缓存名称
     */
    private String extractCacheName(ProceedingJoinPoint joinPoint) {
        try {
            Method method = ((org.aspectj.lang.reflect.MethodSignature) joinPoint.getSignature()).getMethod();
            Cacheable cacheable = method.getAnnotation(Cacheable.class);

            if (cacheable != null) {
                String[] cacheNames = cacheable.cacheNames();
                if (cacheNames.length > 0) {
                    return cacheNames[0];
                }
                String[] value = cacheable.value();
                if (value.length > 0) {
                    return value[0];
                }
            }
        } catch (Exception e) {
            log.warn("提取缓存名称失败", e);
        }
        return "unknown";
    }

    /**
     * 检查缓存是否包含该值
     */
    private boolean isCacheHit(String cacheName, Object[] args) {
        try {
            if (cacheManager == null) {
                return false;
            }

            Cache cache = cacheManager.getCache(cacheName);
            if (cache == null) {
                return false;
            }

            // 生成缓存键（简化版本）
            String cacheKey = generateCacheKey(args);
            Cache.ValueWrapper valueWrapper = cache.get(cacheKey);

            return valueWrapper != null;
        } catch (Exception e) {
            log.warn("检查缓存命中失败，缓存: {}", cacheName, e);
            return false;
        }
    }

    /**
     * 从方法参数生成缓存键
     */
    private String generateCacheKey(Object[] args) {
        if (args == null || args.length == 0) {
            return "default";
        }

        StringBuilder keyBuilder = new StringBuilder();
        for (Object arg : args) {
            if (arg != null) {
                keyBuilder.append(arg.toString()).append(":");
            }
        }

        return keyBuilder.toString();
    }
}