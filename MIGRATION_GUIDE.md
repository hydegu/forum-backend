# 微服务迁移指南

## 已完成的工作

### 1. forum-common模块 ✅
- ✅ 添加了公共依赖（Spring Web, Validation, Lombok）
- ✅ 创建了公共类：
  - `com.example.forum.common.enums.Code` - 状态码枚举
  - `com.example.forum.common.dto.Result` - 统一响应结果
  - `com.example.forum.common.exception.*` - 异常处理类
  - `com.example.forum.common.vo.PageResponse` - 分页响应

### 2. 各微服务依赖配置 ✅
- ✅ forum-user: 添加了MySQL、Redis、MyBatis-Plus、Security、JWT、Mail等依赖
- ✅ forum-post: 添加了MySQL、Redis、MyBatis-Plus等依赖
- ✅ forum-comment: 添加了MySQL、Redis、MyBatis-Plus等依赖

### 3. 数据库和Redis配置 ✅
- ✅ 所有微服务的application.yml已配置数据库连接
- ✅ 已配置Redis连接
- ✅ 已配置MyBatis-Plus

### 4. 用户微服务部分迁移 ✅
- ✅ 实体类：AppUser, RefreshToken, UserFollow
- ✅ Repository：UserRepo, RefreshTokenRepo, UserFollowRepo

## 待完成的工作

### 用户微服务 (forum-user)
1. **Service层**：
   - UserService & UserServiceImpl
   - RefreshTokenService & RefreshTokenServiceImpl
   - VerificationCodeService & VerificationCodeServiceImpl
   - UserFollowService & UserFollowServiceImpl
   - CustomUserDetailsService

2. **Controller层**：
   - UserController (登录、注册、验证码等)
   - AuthController (认证、刷新token等)
   - AdminUserController (管理员用户管理)
   - UserFollowController (用户关注)

3. **配置类**：
   - JwtProperties
   - JwtAuthenticationFilter
   - JwtAuthenticationEntryPoint
   - JwtAccessDeniedHandler
   - UserSecurityConfig
   - RedisConfig
   - MybatisPlusConfig

4. **DTO和VO**：
   - LoginRequest, RegRequest, EmailRequest等
   - LoginResponse, UserProfileResponse, AdminUserSummary等

5. **工具类**：
   - JwtUtils, SecurityUtils, CodeUtils, IpUtils

### 帖子微服务 (forum-post)
1. **实体类**：Post, Category, PostLike
2. **Repository**：PostRepo, CategoryRepo, PostLikeRepo
3. **Service层**：PostService, CategoryService, PostLikeService, MetricsService
4. **Controller层**：PostController, AdminPostController, CategoryController
5. **DTO和VO**：PostCreateRequest, PostDetailView等
6. **配置类**：RedisConfig, MybatisPlusConfig, MetricsConfig
7. **Job**：PostMetricsSyncJob, TrendingPostRefreshJob

### 评论微服务 (forum-comment)
1. **实体类**：PostComment
2. **Repository**：PostCommentRepo
3. **Service层**：CommentService
4. **Controller层**：从PostController中提取评论相关接口
5. **DTO和VO**：PostCommentCreateRequest, CommentTreeNode

### 服务间调用 (OpenFeign)
1. **UserClient**：提供用户信息查询接口（供Post/Comment服务调用）
2. **PostClient**：提供帖子信息查询接口（如需要）

### Gateway路由配置
1. 更新路由规则，确保所有API正确路由

## 迁移步骤

### 步骤1: 完成用户微服务迁移
1. 复制所有Service实现类，调整包名
2. 复制所有Controller，调整包名和import
3. 复制配置类，调整包名
4. 复制DTO/VO，调整包名
5. 复制工具类，调整包名
6. 更新所有import语句，使用forum-common中的公共类

### 步骤2: 完成帖子微服务迁移
1. 复制实体类、Repository、Service、Controller
2. 创建Feign客户端调用User服务获取用户信息
3. 配置Redis和MyBatis-Plus

### 步骤3: 完成评论微服务迁移
1. 复制评论相关代码
2. 创建Feign客户端调用User和Post服务

### 步骤4: 测试和验证
1. 启动所有服务
2. 验证Nacos注册
3. 测试Gateway路由
4. 测试服务间调用

## 注意事项

1. **包名调整**：所有迁移的代码需要将包名从 `com.example.forum.*` 调整为 `com.example.forum.{service}.*`
2. **公共类引用**：使用 `com.example.forum.common.*` 中的公共类
3. **数据库连接**：确保各微服务能连接到同一个数据库（目前设计）
4. **Redis连接**：确保Redis配置正确
5. **跨服务调用**：使用OpenFeign进行服务间调用，避免直接数据库访问

## 下一步操作

请按照上述步骤继续完成代码迁移。如果需要，我可以继续帮助完成剩余部分的迁移。
