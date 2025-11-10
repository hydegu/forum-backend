# 网关JWT验证配置指南

## 概述

网关已添加JWT验证功能，提供双重认证安全架构：
- **第一道防线**：网关验证JWT，拒绝无效请求
- **第二道防线**：User微服务验证JWT，防止绕过攻击

## 架构说明

```
客户端 → 网关[验证JWT] → 微服务[再次验证JWT]
                ↓ 设置请求头
         X-User-Id: 123
         X-Username: john
```

**优点**：
- ✅ 双重验证，安全性最高
- ✅ 现有微服务代码无需修改
- ✅ Feign拦截器继续工作
- ✅ 支持灵活的白名单配置

## 配置步骤

### 1. 在Nacos中配置白名单

登录Nacos控制台（http://localhost:8848/nacos），编辑 `forum-gateway.yaml` 配置文件：

```yaml
server:
  port: 9090

# JWT配置（从 forum-common.yaml 继承）
com:
  jwt:
    # 白名单配置：以下路径不需要JWT验证
    whitelist:
      # 用户服务公开接口
      - /api/user/login
      - /api/user/register
      - /api/user/send-verification-email
      - /api/user/validate-verification-code
      - /api/user/health
      
      # 认证服务公开接口
      - /api/auth/refresh
      - /api/auth/password-reset/**
      
      # 登出接口（可选，根据业务需求）
      - /api/logout
      - /api/auth/logout
      
      # 健康检查和监控
      - /actuator/health
      - /actuator/info
      
      # 帖子服务公开接口（如果需要）
      - /api/post/list
      - /api/post/detail/**
      - /api/post/*/view
      
      # 评论服务公开接口（如果需要）
      - /api/comment/list

spring:
  cloud:
    gateway:
      routes:
        # 用户服务路由
        - id: user-service
          uri: lb://forum-user-service
          predicates:
            - Path=/api/user/**
          filters:
            - StripPrefix=1

        # 帖子服务路由
        - id: post-service
          uri: lb://forum-post-service
          predicates:
            - Path=/api/post/**
          filters:
            - StripPrefix=1

        # 评论服务路由
        - id: comment-service
          uri: lb://forum-comment-service
          predicates:
            - Path=/api/comment/**
          filters:
            - StripPrefix=1
```

### 2. 白名单路径匹配规则

支持Ant风格路径匹配：

| 模式 | 说明 | 示例 |
|------|------|------|
| `?` | 匹配单个字符 | `/api/user/?` 匹配 `/api/user/1` |
| `*` | 匹配0个或多个字符（不包括/） | `/api/user/*` 匹配 `/api/user/login` |
| `**` | 匹配0个或多个路径段 | `/api/user/**` 匹配 `/api/user/a/b/c` |

**示例**：
- `/api/login` - 精确匹配
- `/api/post/*` - 匹配 `/api/post/list`、`/api/post/create`
- `/api/post/**` - 匹配 `/api/post/detail/123/comments`

### 3. JWT验证流程

#### 成功流程：
```
1. 客户端发送请求 → 携带JWT token
2. 网关检查路径 → 不在白名单
3. 网关提取token → 从Header/Cookie
4. 网关验证JWT → 签名和过期时间
5. 验证通过 → 添加 X-User-Id、X-Username 请求头
6. 转发到微服务 → 微服务再次验证JWT
7. 返回响应
```

#### 失败流程：
```
1. 客户端发送请求 → 无token或token无效
2. 网关验证失败 → 返回401 Unauthorized
3. 请求被拦截 → 不会到达微服务
```

## Token传递方式

网关支持三种JWT传递方式（按优先级）：

### 方式1：Authorization头（推荐）
```http
GET /api/user/profile HTTP/1.1
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 方式2：自定义头
```http
GET /api/user/profile HTTP/1.1
authentication: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 方式3：Cookie
```http
GET /api/user/profile HTTP/1.1
Cookie: authentication=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## 测试验证

### 1. 测试白名单路径（不需要JWT）
```bash
# 登录接口，无需JWT
curl -X POST http://localhost:9090/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 应该返回200和JWT token
```

### 2. 测试保护接口（需要JWT）
```bash
# 不带token访问
curl http://localhost:9090/api/user/profile

# 应该返回401 Unauthorized

# 带token访问
curl http://localhost:9090/api/user/profile \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

# 应该返回200和用户信息
```

### 3. 测试无效token
```bash
curl http://localhost:9090/api/user/profile \
  -H "Authorization: Bearer invalid_token"

# 应该返回401 Unauthorized，消息: "认证失败"
```

## 日志说明

网关JWT验证会输出以下日志：

```
# 白名单路径
路径 /api/user/login 在白名单中，跳过JWT验证

# 验证成功
JWT验证成功，用户: john, 路径: /api/user/profile

# 缺少token
请求路径 /api/user/profile 缺少JWT token

# 验证失败
JWT验证失败: JWT expired at 2025-11-10T10:00:00Z, 路径: /api/user/profile
```

## 性能影响

- **JWT解析时间**：约3-5ms
- **双重验证开销**：网关5ms + User服务5ms = 10ms
- **对用户体验影响**：几乎无感知（API总响应时间通常>100ms）

## 安全建议

1. **JWT密钥管理**
   - 使用强随机密钥（至少256位）
   - 定期轮换密钥
   - 不要在代码中硬编码密钥

2. **Token有效期**
   - Access Token：2小时
   - Refresh Token：7天
   - 根据业务需求调整

3. **HTTPS**
   - 生产环境必须使用HTTPS
   - 防止JWT被中间人截获

4. **内网隔离**
   - 确保微服务端口（8081/8082/8083）不对外暴露
   - 仅允许网关和内网访问

5. **白名单最小化**
   - 只添加真正需要公开的接口
   - 定期审查白名单配置

## 故障排查

### 问题1：所有请求返回401
**原因**：白名单配置错误或JWT密钥不匹配

**解决**：
1. 检查Nacos中`com.jwt.user-secret-key`是否正确
2. 确认白名单路径配置正确
3. 查看网关日志

### 问题2：网关通过但微服务返回401
**原因**：微服务JWT配置与网关不一致

**解决**：
1. 确认User服务的JWT密钥与网关一致（都从Nacos读取）
2. 检查User服务的`JwtAuthenticationFilter`是否正常工作

### 问题3：Feign调用失败
**原因**：Feign拦截器未传递JWT

**解决**：
1. 确认`FeignRequestInterceptor`已注册
2. 检查Feign调用日志

## 高级配置

### 动态调整白名单（无需重启）

Nacos支持配置热更新，修改`forum-gateway.yaml`后：
1. 网关自动刷新配置
2. 白名单立即生效
3. 无需重启服务

### 自定义响应格式

如果需要修改401响应格式，编辑`JwtGatewayFilter.java`中的`unauthorized`方法。

## 总结

- ✅ 网关JWT验证已配置完成
- ✅ 现有代码无需修改
- ✅ 双重验证提供最高安全性
- ✅ 灵活的白名单配置
- ✅ 支持配置热更新

如有问题，请查看网关日志：`logs/forum-gateway.log`
