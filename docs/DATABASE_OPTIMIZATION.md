# 数据库优化指南

## 索引建议

基于论坛系统的查询模式，以下是推荐的数据库索引：

### Post 表索引

```sql
-- 主键索引（自动创建）
-- PRIMARY KEY (id)

-- 作者查询索引
CREATE INDEX idx_post_author_id ON post(author_id);

-- 分类查询索引
CREATE INDEX idx_post_category_id ON post(category_id);

-- 状态过滤索引
CREATE INDEX idx_post_status ON post(status);

-- 创建时间排序索引（用于最新文章列表）
CREATE INDEX idx_post_created_at ON post(created_at DESC);

-- 浏览量排序索引（用于热门文章）
CREATE INDEX idx_post_view_count ON post(view_count DESC);

-- 点赞数排序索引
CREATE INDEX idx_post_like_count ON post(like_count DESC);

-- 组合索引：状态 + 创建时间（优化已审核文章查询）
CREATE INDEX idx_post_status_created ON post(status, created_at DESC);

-- 组合索引：状态 + 分类 + 创建时间
CREATE INDEX idx_post_status_category_created ON post(status, category_id, created_at DESC);

-- 全文搜索索引（如果需要）
-- CREATE FULLTEXT INDEX idx_post_fulltext ON post(title, subtitle, content);
```

### PostComment 表索引

```sql
-- 主键索引（自动创建）
-- PRIMARY KEY (id)

-- 文章评论查询索引
CREATE INDEX idx_comment_post_id ON post_comment(post_id);

-- 根评论查询索引
CREATE INDEX idx_comment_root_id ON post_comment(root_id);

-- 父评论查询索引
CREATE INDEX idx_comment_parent_id ON post_comment(parent_id);

-- 用户评论查询索引
CREATE INDEX idx_comment_user_id ON post_comment(user_id);

-- 创建时间排序索引
CREATE INDEX idx_comment_created_at ON post_comment(created_at DESC);

-- 组合索引：文章 + 根评论（优化评论树查询）
CREATE INDEX idx_comment_post_root ON post_comment(post_id, root_id);

-- 组合索引：文章 + 创建时间
CREATE INDEX idx_comment_post_created ON post_comment(post_id, created_at DESC);
```

### AppUser 表索引

```sql
-- 主键索引（自动创建）
-- PRIMARY KEY (id)

-- 用户名唯一索引（用于登录）
CREATE UNIQUE INDEX idx_user_username ON app_user(username);

-- 邮箱唯一索引（用于注册/找回密码）
CREATE UNIQUE INDEX idx_user_email ON app_user(email);

-- 状态索引
CREATE INDEX idx_user_status ON app_user(status);

-- 角色索引
CREATE INDEX idx_user_role ON app_user(role);

-- 创建时间索引
CREATE INDEX idx_user_created_at ON app_user(created_at DESC);
```

## 慢查询监控配置

### MySQL 慢查询日志配置

在 MySQL 配置文件（my.cnf 或 my.ini）中添加：

```ini
[mysqld]
# 开启慢查询日志
slow_query_log = 1

# 慢查询日志文件路径
slow_query_log_file = /var/log/mysql/slow-query.log

# 慢查询阈值（秒）
long_query_time = 1

# 记录未使用索引的查询
log_queries_not_using_indexes = 1

# 限制每分钟记录的未使用索引的查询数量
log_throttle_queries_not_using_indexes = 10
```

### 分析慢查询

```bash
# 使用 mysqldumpslow 分析慢查询日志
mysqldumpslow -s t -t 10 /var/log/mysql/slow-query.log

# 参数说明：
# -s t: 按查询时间排序
# -t 10: 显示前 10 条
# -s c: 按查询次数排序
# -s l: 按锁定时间排序
```

## 查询优化建议

### 1. 使用覆盖索引

```sql
-- 不好的查询（需要回表）
SELECT * FROM post WHERE status = 'approved' ORDER BY created_at DESC LIMIT 10;

-- 好的查询（使用覆盖索引）
SELECT id, title, author_id, created_at
FROM post
WHERE status = 'approved'
ORDER BY created_at DESC
LIMIT 10;
```

### 2. 避免 SELECT *

```sql
-- 不好的做法
SELECT * FROM post WHERE id = 1;

-- 好的做法：只查询需要的字段
SELECT id, title, content, author_id, created_at FROM post WHERE id = 1;
```

### 3. 使用分页查询优化

```sql
-- 不好的分页（深度分页性能差）
SELECT * FROM post ORDER BY created_at DESC LIMIT 10000, 10;

-- 好的分页（使用游标）
SELECT * FROM post
WHERE created_at < '2024-01-01 00:00:00'
ORDER BY created_at DESC
LIMIT 10;
```

### 4. 使用批量操作

```java
// 不好的做法：循环插入
for (Comment comment : comments) {
    commentRepo.insert(comment);
}

// 好的做法：批量插入
commentRepo.insertBatch(comments);
```

### 5. 避免 N+1 查询问题

```java
// 不好的做法：产生 N+1 查询
List<Post> posts = postRepo.selectList(null);
for (Post post : posts) {
    User author = userRepo.selectById(post.getAuthorId()); // N 次查询
}

// 好的做法：使用 JOIN 或批量查询
List<Post> posts = postRepo.selectPostsWithAuthors();
```

## MyBatis-Plus 查询优化

### 1. 使用 QueryWrapper 构建复杂查询

```java
QueryWrapper<Post> wrapper = new QueryWrapper<>();
wrapper.eq("status", "approved")
       .orderByDesc("created_at")
       .last("LIMIT 10");
List<Post> posts = postRepo.selectList(wrapper);
```

### 2. 使用 LambdaQueryWrapper（类型安全）

```java
LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Post::getStatus, "approved")
       .orderByDesc(Post::getCreatedAt)
       .last("LIMIT 10");
```

### 3. 字段选择

```java
// 只查询部分字段
LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
wrapper.select(Post::getId, Post::getTitle, Post::getCreatedAt)
       .eq(Post::getStatus, "approved");
```

## 性能监控

### 1. 监控慢查询数量

```prometheus
# 慢查询总数
rate(forum_database_slow_queries_total[5m])

# 按语句分组的慢查询
rate(forum_database_slow_queries_total[5m]) by (statement)
```

### 2. 监控查询延迟

```prometheus
# P95 查询延迟
histogram_quantile(0.95, rate(forum_database_query_seconds_bucket[5m]))

# P99 查询延迟
histogram_quantile(0.99, rate(forum_database_query_seconds_bucket[5m]))
```

### 3. 监控数据库连接池

在 application.yml 中配置 HikariCP 监控：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      # 开启 JMX 监控
      register-mbeans: true
```

## 定期维护

### 1. 分析表

```sql
-- 分析表统计信息（帮助优化器选择正确的索引）
ANALYZE TABLE post;
ANALYZE TABLE post_comment;
ANALYZE TABLE app_user;
```

### 2. 优化表

```sql
-- 整理表碎片
OPTIMIZE TABLE post;
OPTIMIZE TABLE post_comment;
```

### 3. 检查索引使用情况

```sql
-- 查看索引统计信息
SHOW INDEX FROM post;

-- 查看未使用的索引
SELECT * FROM sys.schema_unused_indexes;
```

## 告警规则

建议设置以下告警：

1. **慢查询数量过多**: 当每分钟慢查询数 > 10 时告警
2. **查询延迟过高**: 当 P95 查询延迟 > 1s 时告警
3. **连接池耗尽**: 当活跃连接数 / 最大连接数 > 0.8 时告警
4. **死锁检测**: 监控 MySQL 死锁日志