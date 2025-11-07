# 论坛系统监控与日志完整指南

## 目录

1. [概述](#概述)
2. [快速开始](#快速开始)
3. [日志系统](#日志系统)
4. [监控系统](#监控系统)
5. [告警配置](#告警配置)
6. [故障排查](#故障排查)
7. [性能优化](#性能优化)

---

## 概述

本论坛系统集成了完整的监控和日志解决方案，包括：

- **Spring Boot Actuator**: 应用健康检查和管理端点
- **Prometheus**: 时序数据库，存储监控指标
- **Grafana**: 可视化监控面板
- **Logback + MDC**: 结构化日志和请求追踪
- **Micrometer**: 应用指标收集框架

### 架构图

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   应用层    │ ───> │  Actuator   │ ───> │ Prometheus  │
│ (Spring)    │      │  Metrics    │      │             │
└─────────────┘      └─────────────┘      └──────┬──────┘
                                                  │
       │                                          │
       │ 日志                                     │ 指标
       ▼                                          ▼
┌─────────────┐                           ┌─────────────┐
│  Logback    │                           │   Grafana   │
│  (JSON/TXT) │                           │ (Dashboard) │
└─────────────┘                           └─────────────┘
```

---

## 快速开始

### 1. 启动应用

```bash
# 启动 Spring Boot 应用
./mvnw spring-boot:run
```

应用启动后，可以访问：
- 应用地址: http://localhost:8080
- Actuator 健康检查: http://localhost:8080/actuator/health
- Prometheus 指标: http://localhost:8080/actuator/prometheus

### 2. 启动监控栈

```bash
# 启动 Prometheus 和 Grafana
docker-compose -f docker-compose.monitoring.yml up -d

# 查看运行状态
docker-compose -f docker-compose.monitoring.yml ps
```

监控服务地址：
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

### 3. 配置 Grafana 仪表板

1. 登录 Grafana (http://localhost:3000)
2. 数据源已自动配置（Prometheus）
3. 导入推荐仪表板：
   - JVM (Micrometer): **4701**
   - Spring Boot Statistics: **6756**
   - 详见 `monitoring/grafana/DASHBOARD_SETUP.md`

---

## 日志系统

### 日志配置

日志配置文件: `src/main/resources/logback-spring.xml`

#### 日志输出位置

1. **控制台日志**: 带颜色的人类可读格式
2. **文本日志**: `logs/forum.log`（人类可读）
3. **JSON 日志**: `logs/forum-json.log`（ELK 兼容）
4. **错误日志**: `logs/forum-error.log`（仅 ERROR 级别）

#### 日志级别

在 `application.yml` 中配置：

```yaml
logging:
  level:
    root: INFO
    com.example.forum: DEBUG
    org.springframework.web: DEBUG
    org.springframework.security: DEBUG
```

### MDC 请求追踪

每个请求自动注入以下 MDC 字段：

- `traceId`: 唯一请求 ID
- `userId`: 当前用户 ID（如果已认证）
- `requestMethod`: HTTP 方法
- `requestUri`: 请求路径
- `clientIp`: 客户端 IP

#### 日志格式示例

**控制台日志**:
```
2024-01-15 10:30:45.123 INFO  [http-nio-8080-exec-1] [a1b2c3d4e5f6g7h8] [12345] c.e.f.controller.PostController - Creating new post
```

**JSON 日志**:
```json
{
  "timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.example.forum.controller.PostController",
  "message": "Creating new post",
  "traceId": "a1b2c3d4e5f6g7h8",
  "userId": "12345",
  "requestMethod": "POST",
  "requestUri": "/api/posts"
}
```

### 查看日志

```bash
# 查看最新日志
tail -f logs/forum.log

# 查看错误日志
tail -f logs/forum-error.log

# 查看 JSON 日志
tail -f logs/forum-json.log | jq .

# 按 traceId 搜索日志
grep "a1b2c3d4e5f6g7h8" logs/forum.log
```

---

## 监控系统

### Actuator 端点

#### 公开端点（无需认证）

- `/actuator/health` - 健康检查
- `/actuator/info` - 应用信息
- `/actuator/prometheus` - Prometheus 指标
- `/actuator/metrics/**` - 指标详情

#### 受保护端点（需要认证）

- `/actuator/env` - 环境变量
- `/actuator/loggers` - 日志级别管理
- `/actuator/caches` - 缓存管理
- `/actuator/threaddump` - 线程转储
- `/actuator/heapdump` - 堆转储

#### 示例请求

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 查看所有指标
curl http://localhost:8080/actuator/metrics

# 查看 JVM 内存指标
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Prometheus 格式指标
curl http://localhost:8080/actuator/prometheus
```

### 自定义业务指标

应用暴露以下自定义指标：

#### 用户指标
- `forum_user_registration_total` - 用户注册总数

#### 文章指标
- `forum_post_created_total` - 文章创建总数
- `forum_post_views_total` - 文章浏览总数
- `forum_post_likes_total` - 文章点赞总数
- `forum_post_updated_total` - 文章更新总数
- `forum_post_deleted_total` - 文章删除总数

#### 评论指标
- `forum_comment_created_total` - 评论创建总数
- `forum_comment_deleted_total` - 评论删除总数

#### 认证指标
- `forum_auth_login_success_total` - 登录成功总数
- `forum_auth_login_failure_total` - 登录失败总数

#### 缓存指标
- `forum_cache_hit_total` - 缓存命中总数
- `forum_cache_miss_total` - 缓存未命中总数

#### 性能指标
- `forum_database_query_seconds` - 数据库查询耗时
- `forum_redis_operation_seconds` - Redis 操作耗时
- `forum_business_operation_seconds` - 业务操作耗时

#### 通知指标
- `forum_email_sent_total` - 邮件发送总数
- `forum_email_failure_total` - 邮件发送失败总数

### 使用 MetricsService

在代码中记录业务指标：

```java
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final MetricsService metricsService;

    @Override
    public Post createPost(PostDTO dto) {
        // 记录文章创建
        metricsService.recordPostCreation();

        // 业务逻辑...
        return post;
    }

    @Override
    public Post getPost(Long id) {
        // 记录文章浏览
        metricsService.recordPostView(id);

        // 业务逻辑...
        return post;
    }
}
```

### Prometheus 查询示例

在 Prometheus UI (http://localhost:9090) 中执行查询：

```promql
# 每秒用户注册速率
rate(forum_user_registration_total[5m])

# 登录成功率
rate(forum_auth_login_success_total[5m]) /
(rate(forum_auth_login_success_total[5m]) + rate(forum_auth_login_failure_total[5m]))

# 缓存命中率
rate(forum_cache_hit_total[5m]) /
(rate(forum_cache_hit_total[5m]) + rate(forum_cache_miss_total[5m]))

# P95 API 响应时间
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# P99 数据库查询时间
histogram_quantile(0.99, rate(forum_database_query_seconds_bucket[5m]))

# JVM 堆内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
```

---

## Grafana 仪表板

### 导入社区仪表板

1. 访问 Grafana: http://localhost:3000
2. 点击 "+" -> "Import"
3. 输入仪表板 ID：

| 仪表板 | ID | 说明 |
|--------|----|----|
| JVM (Micrometer) | 4701 | JVM 内存、线程、GC |
| Spring Boot Statistics | 6756 | 请求统计、错误率 |
| Spring Boot System Monitor | 11378 | 系统资源、健康状态 |

### 自定义面板

创建自定义面板监控业务指标，参考 `monitoring/grafana/DASHBOARD_SETUP.md`

---

## 告警配置

### Prometheus 告警规则

创建 `monitoring/alert_rules.yml`:

```yaml
groups:
  - name: forum_alerts
    rules:
      # 高错误率告警
      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 10
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} requests/sec"

      # 慢请求告警
      - alert: SlowRequests
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Slow requests detected"
          description: "P95 response time is {{ $value }}s"

      # 高 CPU 告警
      - alert: HighCPU
        expr: process_cpu_usage > 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage"
          description: "CPU usage is {{ $value | humanizePercentage }}"

      # 内存告警
      - alert: HighMemory
        expr: jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} > 0.85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage"
          description: "Heap memory usage is {{ $value | humanizePercentage }}"

      # 低缓存命中率告警
      - alert: LowCacheHitRate
        expr: rate(forum_cache_hit_total[5m]) / (rate(forum_cache_hit_total[5m]) + rate(forum_cache_miss_total[5m])) < 0.7
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Low cache hit rate"
          description: "Cache hit rate is {{ $value | humanizePercentage }}"
```

---

## 故障排查

### 应用无法启动

1. 检查日志: `logs/forum.log`
2. 检查端口占用: `netstat -ano | findstr 8080`
3. 检查数据库连接: `application.yml` 中的数据库配置
4. 检查 Redis 连接: `application.yml` 中的 Redis 配置

### 性能问题

1. **慢请求排查**:
   ```bash
   # 查看慢查询日志
   grep "SLOW QUERY" logs/forum.log

   # 查看 Prometheus 慢查询指标
   curl http://localhost:8080/actuator/metrics/forum.database.slow_queries
   ```

2. **内存泄漏排查**:
   ```bash
   # 下载堆转储
   curl -O http://localhost:8080/actuator/heapdump

   # 使用 jhat 或 VisualVM 分析
   jhat heapdump
   ```

3. **线程问题排查**:
   ```bash
   # 获取线程转储
   curl http://localhost:8080/actuator/threaddump > threaddump.json
   ```

### 缓存问题

1. **缓存命中率低**:
   - 检查 `CacheWarmupService` 是否正常运行
   - 查看 Grafana 缓存命中率面板
   - 检查缓存 TTL 配置

2. **缓存不一致**:
   ```bash
   # 清除所有缓存（通过管理接口）
   curl -X POST http://localhost:8080/api/admin/cache/clear
   ```

---

## 性能优化

### 1. 数据库优化

参考 `docs/DATABASE_OPTIMIZATION.md` 进行：
- 添加合适的索引
- 优化慢查询
- 配置连接池

### 2. 缓存优化

- 预热热点数据（启动时自动执行）
- 监控缓存命中率
- 调整缓存 TTL

### 3. 应用优化

- 使用异步处理（如邮件发送）
- 批量操作代替循环
- 使用分页查询

---

## 生产环境建议

### 1. 日志管理

- 使用 ELK Stack (Elasticsearch + Logstash + Kibana) 集中管理日志
- 配置日志轮转和归档策略
- 定期清理旧日志

### 2. 监控告警

- 配置 Alertmanager 发送告警通知
- 设置合理的告警阈值
- 建立值班响应流程

### 3. 安全加固

- 限制 Actuator 端点访问（通过 Spring Security）
- 使用 HTTPS
- 定期更新依赖

### 4. 备份策略

- 定期备份 Prometheus 数据
- 定期备份 Grafana 仪表板配置
- 定期备份应用日志

---

## 参考资源

- [Spring Boot Actuator 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Prometheus 文档](https://prometheus.io/docs/)
- [Grafana 文档](https://grafana.com/docs/)
- [Micrometer 文档](https://micrometer.io/docs)
- [Logback 文档](http://logback.qos.ch/documentation.html)

---

## 支持

如有问题，请检查：
1. 应用日志: `logs/forum.log`
2. Actuator 健康检查: http://localhost:8080/actuator/health
3. Prometheus 目标状态: http://localhost:9090/targets