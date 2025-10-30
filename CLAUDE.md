# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.6 forum application with JWT authentication, Redis caching, and MySQL database. Uses MyBatis-Plus for ORM and includes scheduled jobs for metrics synchronization.

## Build & Run Commands

### Development
```bash
# Run application
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Clean and package
./mvnw clean package

# Run tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=ForumApplicationTests
```

### Docker & Native Build
```bash
# Build Docker image with GraalVM native
./mvnw spring-boot:build-image -Pnative

# Run Docker container
docker run --rm -p 8080:8080 Forum:0.0.1-SNAPSHOT

# Build native executable
./mvnw native:compile -Pnative

# Run native tests
./mvnw test -PnativeTest
```

## Architecture

### Layered Structure
- **Controllers** (`controller/`): REST API endpoints at `/api/*`
- **Services** (`service/`): Business logic with interface + implementation pattern
- **Repositories** (`repo/`): MyBatis-Plus mappers for data access
- **Entities** (`entity/`): Database models using MyBatis-Plus annotations
- **DTOs** (`dto/`): Request objects for API endpoints
- **VOs** (`vo/`): Response/view objects returned to clients
- **Config** (`config/`): Spring configuration classes
- **Jobs** (`job/`): Scheduled tasks using `@Scheduled`

### Core Domain Models

**AppUser** (`entity/AppUser.java`): User accounts with roles (user/admin), status, profile info

**Post** (`entity/Post.java`): Forum posts with:
- Content fields: title, subtitle, content, images (JSON array)
- Metrics: viewCount, likeCount, commentCount (synced from Redis)
- Relationships: authorId, categoryId
- Flags: status (pending/approved/rejected), pinned

**PostComment** (`entity/PostComment.java`): Threaded comments with parent/root structure
- rootId: Top-level comment in thread
- parentId: Direct parent comment

### Authentication Flow

1. **Registration**: Email verification required via Redis-stored codes
2. **Login**: Returns JWT access token (2 hours TTL) + refresh token (7 days TTL) as HTTP-only cookies
3. **Token Refresh**: Use refresh token at `/api/auth/refresh` to get new access token
4. **JWT Filter**: `JwtAuthenticationFilter` validates tokens on protected endpoints

JWT configuration in `application.yml`:
- Access token: `com.jwt.user-secret-key`, `user-ttl: 7200000` (2 hours)
- Refresh token: `com.jwt.refresh-secret-key`, `refresh-ttl: 604800000` (7 days)

### Security Configuration

`UserSecurityConfig.java` defines public vs authenticated endpoints:
- **Public**: `/api/login`, `/api/register`, email verification, password reset, GET posts/comments
- **Authenticated**: POST/PUT/DELETE operations, user profile, admin endpoints
- **CORS**: Configured for `http://localhost:5173` (frontend)

### Caching Strategy

Redis caching with Spring Cache abstraction (`RedisConfig.java`):

**Cache Names & TTLs**:
- `posts:list`: 3 minutes - paginated post lists
- `posts:detail`: 5 minutes - individual post details
- `comments:page`: 2 minutes - paginated comments for a post
- `users:profile`: 30 minutes - user profile data

**Cache Keys**:
- Posts list: `{current}:{pageSize}:{status}:{q}:{categoryId}`
- Comments page: `{postId}:{page}:{size}`

**Cache Invalidation Patterns**:

For comments (`CommentServiceImpl.java`):
- **addComment/deleteComment**: Use `@CacheEvict(cacheNames = "comments:page", allEntries = true)` to clear all comment pages for the post
- For precise control: Clear specific `{postId}:{page}:{size}` and `{postId}:{rootId}` cache entries
- Reduce inconsistency: Write updated data back to cache at method end

For posts:
- Post creation/update/deletion should evict `posts:list` and `posts:detail` caches
- Use `@Caching` to evict multiple cache regions

**Metrics Caching**:
- Post metrics (views, likes, comments) are incremented in Redis: `post:metrics:{postId}`
- Format: Hash with fields `views`, `likes`, `comments`
- Synced to database every 5 minutes by `PostMetricsSyncJob`

### Scheduled Jobs

**PostMetricsSyncJob** (`job/PostMetricsSyncJob.java`):
- Interval: 5 minutes (configurable: `forum.metrics.flush-interval`)
- Scans Redis keys matching `post:metrics:*`
- Increments database counters and clears Redis deltas
- Uses `@Transactional` for atomicity

**TrendingPostRefreshJob** (`job/TrendingPostRefreshJob.java`):
- Interval: 10 minutes (configurable: `forum.trending.refresh-interval`)
- Calculates trending posts based on recent activity
- Stores results in Redis for fast retrieval

### Email Verification

`VerificationCodeServiceImpl.java` implements rate limiting:
- Codes stored in Redis: `auth:code:{email}` with 5-minute TTL
- Rate limiting: `auth:quota:{email}:{ip}` - max 5 sends per 10-minute window
- IP-based to prevent abuse

### MyBatis-Plus Configuration

`MybatisPlusConfig.java`:
- Pagination plugin enabled
- Custom mappers in `repo/` package (auto-scanned via `@MapperScan`)
- Entities use `@TableName`, `@TableId(type = IdType.AUTO)`, `@TableField`
- JSON columns handled by `JacksonTypeHandler` (e.g., Post.images)

### File Upload

`UploadServiceImpl.java` + `UploadController.java`:
- Directory: `uploads/` (configurable: `app.upload.directory`)
- Base URL: `/uploads` (configurable: `app.upload.base-url`)
- Max size: 10MB (configurable: `spring.servlet.multipart.max-file-size`)

## Database Connection

Configuration in `application.yml`:
```yaml
spring.datasource:
  url: jdbc:mysql://localhost:3306/forum_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=UTC
  username: root
  password: 123456
```

Redis connection:
```yaml
spring.data.redis:
  host: ${REDIS_HOST:localhost}
  port: ${REDIS_PORT:6379}
  password: ${REDIS_PASSWORD:123456}
  database: ${REDIS_DATABASE:0}
```

## Important Implementation Notes

### Comment Tree Structure
- Root comments have `rootId = null` or `rootId = id`
- Replies have `rootId` pointing to top-level comment
- Direct replies use `parentId` for threading
- `CommentServiceImpl.pageComments()` loads root comments with pagination, then fetches all replies for displayed roots

### Post Status Workflow
- New posts start as `pending` (require admin approval)
- Admin can approve → `approved` or reject → `rejected`
- Only `approved` posts shown to regular users
- Admin endpoints: `AdminPostController` for moderation

### User Roles
- `role` field in AppUser: "user" or "admin"
- Admin-only endpoints protected by role checks in services
- Security configuration allows role-based authorization

### Refresh Token Management
- `RefreshTokenService` stores tokens in database with expiry
- Tokens bound to user and device
- Logout invalidates refresh token
- Expired tokens cleaned up automatically

## Testing Notes

- Test directory: `src/test/java/com/example/forum/`
- Use `@SpringBootTest` for integration tests
- Mock authentication: Use `@WithMockUser` or manually set `SecurityContextHolder`
- Database: Consider using H2 or Testcontainers for test isolation

## Monitoring & Logging

### Actuator Endpoints

**Public endpoints** (no auth required):
- `/actuator/health` - Health check
- `/actuator/info` - Application info
- `/actuator/prometheus` - Prometheus metrics
- `/actuator/metrics/**` - Metrics details

**Protected endpoints** (auth required):
- `/actuator/env` - Environment variables
- `/actuator/loggers` - Log level management
- `/actuator/caches` - Cache management
- `/actuator/threaddump` - Thread dump
- `/actuator/heapdump` - Heap dump

### Custom Business Metrics

Use `MetricsService` to record business events:

```java
@Service
@RequiredArgsConstructor
public class YourService {
    private final MetricsService metricsService;

    public void createPost() {
        metricsService.recordPostCreation();
        // ... business logic
    }
}
```

Available metrics methods:
- `recordUserRegistration()` - Track user registrations
- `recordPostCreation()` / `recordPostView(id)` / `recordPostLike(id)` - Track post operations
- `recordCommentCreation()` / `recordCommentDeletion()` - Track comment operations
- `recordLoginSuccess(username)` / `recordLoginFailure(username)` - Track authentication
- `recordCacheHit(cacheName)` / `recordCacheMiss(cacheName)` - Track cache performance
- `recordEmailSent(type)` / `recordEmailFailure(type)` - Track email operations

### Logging with MDC

All requests automatically include MDC context:
- `traceId` - Unique request identifier
- `userId` - Current user ID (if authenticated)
- `requestMethod` - HTTP method
- `requestUri` - Request path
- `clientIp` - Client IP address

Log files locations:
- `logs/forum.log` - Main application log (text format)
- `logs/forum-json.log` - Structured JSON logs (ELK-compatible)
- `logs/forum-error.log` - Error-level logs only

### Monitoring Stack

Start Prometheus + Grafana:
```bash
docker-compose -f docker-compose.monitoring.yml up -d
```

Access points:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)

Recommended Grafana dashboards:
- JVM (Micrometer): Dashboard ID **4701**
- Spring Boot Statistics: Dashboard ID **6756**
- Spring Boot System Monitor: Dashboard ID **11378**

### Cache Optimization

`CacheWarmupService` automatically preloads:
- Trending posts (top 20 by views)
- Recent posts (latest 50 approved)

Cache monitoring with `CacheMonitoringAspect` tracks:
- Cache hit/miss rates
- Cache operation metrics

### Database Performance

`SlowQueryInterceptor` monitors and logs queries exceeding 1 second threshold.

Performance metrics:
- `forum.database.query` - Query execution time
- `forum.database.slow_queries` - Slow query counter

See `docs/DATABASE_OPTIMIZATION.md` for index recommendations and query optimization tips.

### Quick Start

1. Start application: `./mvnw spring-boot:run`
2. Start monitoring: `docker-compose -f docker-compose.monitoring.yml up -d`
3. Verify health: `curl http://localhost:8080/actuator/health`
4. View metrics: http://localhost:9090 (Prometheus)
5. View dashboards: http://localhost:3000 (Grafana)

For detailed documentation, see:
- `MONITORING_QUICKSTART.md` - Quick setup guide
- `docs/MONITORING_AND_LOGGING.md` - Complete monitoring guide
- `docs/DATABASE_OPTIMIZATION.md` - Database optimization guide
- `monitoring/grafana/DASHBOARD_SETUP.md` - Grafana dashboard setup
