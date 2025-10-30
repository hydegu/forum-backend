# Grafana Dashboard Setup

## 导入仪表板

访问 Grafana (http://localhost:3000) 后，可以通过以下步骤导入仪表板：

1. 登录 Grafana（默认用户名/密码：admin/admin）
2. 点击左侧菜单 "+" -> "Import"
3. 输入以下社区仪表板 ID 或上传 JSON 文件

## 推荐的 Spring Boot 仪表板

### 1. JVM (Micrometer) Dashboard
- Dashboard ID: **4701**
- 监控内容：JVM 内存、线程、GC、类加载等

### 2. Spring Boot Statistics
- Dashboard ID: **6756**
- 监控内容：请求统计、错误率、响应时间

### 3. Spring Boot 2.1 System Monitor
- Dashboard ID: **11378**
- 监控内容：系统资源、应用健康状态

## 自定义面板建议

为论坛应用创建自定义面板，监控以下指标：

### 业务指标
```
# 用户注册数
rate(forum_user_registration_total[5m])

# 文章创建数
rate(forum_post_created_total[5m])

# 文章浏览量
rate(forum_post_views_total[5m])

# 评论创建数
rate(forum_comment_created_total[5m])

# 登录成功率
rate(forum_auth_login_success_total[5m]) / (rate(forum_auth_login_success_total[5m]) + rate(forum_auth_login_failure_total[5m]))
```

### 缓存指标
```
# 缓存命中率
rate(forum_cache_hit_total[5m]) / (rate(forum_cache_hit_total[5m]) + rate(forum_cache_miss_total[5m]))

# 缓存未命中数
rate(forum_cache_miss_total[5m])
```

### 性能指标
```
# API 响应时间 (P95)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# 数据库查询时间
histogram_quantile(0.95, rate(forum_database_query_seconds_bucket[5m]))

# Redis 操作时间
histogram_quantile(0.95, rate(forum_redis_operation_seconds_bucket[5m]))
```

### 系统指标
```
# JVM 堆内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}

# CPU 使用率
process_cpu_usage

# 活动线程数
jvm_threads_live_threads
```

### 告警规则建议

1. **高错误率**: 当 5xx 错误率超过 1% 时告警
2. **慢请求**: 当 P95 响应时间超过 2 秒时告警
3. **高 CPU**: 当 CPU 使用率超过 80% 时告警
4. **内存不足**: 当堆内存使用率超过 85% 时告警
5. **缓存失效**: 当缓存命中率低于 70% 时告警

## 面板布局建议

```
+------------------+------------------+
|   请求总数       |   错误率         |
+------------------+------------------+
|   响应时间 (P95) |   活跃用户       |
+------------------+------------------+
|   文章/评论创建趋势               |
+-----------------------------------+
|   JVM 堆内存使用                  |
+-----------------------------------+
|   缓存命中率                      |
+-----------------------------------+
```