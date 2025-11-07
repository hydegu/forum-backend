# Forum Backend - 微服务架构

基于Spring Cloud的论坛系统后端，采用Nacos作为配置中心和服务注册中心。

## 技术栈

- Spring Boot 3.x
- Spring Cloud Alibaba
- Nacos (配置中心 + 服务注册)
- Sentinel (熔断降级)
- Gateway (API网关)
- MySQL 8.0
- Redis
- MyBatis-Plus

## 架构说明

本项目采用微服务架构，包含以下服务：

- **forum-application**: API网关服务 (端口: 9090)
- **forum-user**: 用户服务 (端口: 8081)
- **forum-post**: 帖子服务 (端口: 8082)
- **forum-comment**: 评论服务 (端口: 8083)
- **forum-common**: 公共模块

## 快速开始

### 1. 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0
- Redis 6.0+
- Nacos 2.x

### 2. 数据库初始化

```bash
mysql -u root -p < sql/forum_system.sql
```

### 3. 启动Nacos

```bash
# 下载并启动Nacos
# 访问 http://localhost:8848/nacos
# 默认用户名/密码: nacos/nacos
```

### 4. 配置Nacos

在Nacos控制台中创建以下配置文件（命名空间: public, 分组: DEFAULT_GROUP）:

#### 4.1 forum-common.yaml (公共配置)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/forum_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: your_mysql_username
    password: your_mysql_password
    driver-class-name: com.mysql.cj.jdbc.Driver

  # 邮件配置（用于验证码发送）
  mail:
    host: smtp.qq.com
    port: 465
    username: your_email@qq.com
    password: your_qq_email_auth_code  # QQ邮箱授权码
    properties:
      mail.smtp.auth: true
      mail.smtp.ssl.enable: true
      mail.smtp.starttls.enable: true

  # 缓存配置
  cache:
    type: redis

  # Redis配置
  data:
    redis:
      host: localhost
      port: 6379
      password: your_redis_password
      database: 0

# JWT配置
com:
  jwt:
    user-secret-key: your_base64_encoded_secret_key_here
    user-ttl: 7200000  # 2小时
    user-token-name: authentication
    refresh-secret-key: your_base64_encoded_refresh_secret_key_here
    refresh-ttl: 604800000  # 7天
    refresh-token-name: refreshToken

# MyBatis-Plus配置
mybatis-plus:
  configuration:
    default-enum-type-handler: com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler
    map-underscore-to-camel-case: true
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.example.forum.*.entity

# Actuator健康检查
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always

# 日志配置
logging:
  level:
    com.example.forum: INFO
    com.alibaba.cloud: INFO
    com.baomidou.mybatisplus: INFO
```

#### 4.2 forum-user-service.yaml (用户服务配置)

```yaml
server:
  port: 8081

# 上传配置
app:
  upload:
    directory: uploads
    base-url: /uploads

# 缓存配置
forum:
  cache:
    verification-code-ttl: PT5M  # 验证码5分钟过期
    send-limit-window: PT10M     # 10分钟内限制发送次数
    send-limit-max: 5            # 最多发送5次
```

#### 4.3 forum-post-service.yaml (帖子服务配置)

```yaml
server:
  port: 8082

# Feign超时配置
feign:
  client:
    config:
      default:
        connectTimeout: 3000
        readTimeout: 5000
```

#### 4.4 forum-comment-service.yaml (评论服务配置)

```yaml
server:
  port: 8083

# Feign超时配置
feign:
  client:
    config:
      default:
        connectTimeout: 3000
        readTimeout: 5000
```

#### 4.5 forum-gateway.yaml (网关配置)

```yaml
server:
  port: 9090

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

      # 全局CORS配置
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOriginPatterns: "*"
            allowedMethods: "*"
            allowedHeaders: "*"
            allowCredentials: true
```

### 5. 配置说明

#### 5.1 生成JWT密钥

你需要生成自己的JWT密钥。可以使用以下方法：

**方法1: 使用OpenSSL**
```bash
# 生成256位随机密钥并base64编码
openssl rand -base64 32
```

**方法2: 使用Java代码**
```java
import java.security.SecureRandom;
import java.util.Base64;

byte[] key = new byte[32];
new SecureRandom().nextBytes(key);
String encodedKey = Base64.getEncoder().encodeToString(key);
System.out.println(encodedKey);
```

将生成的密钥替换配置中的 `your_base64_encoded_secret_key_here` 和 `your_base64_encoded_refresh_secret_key_here`。

#### 5.2 QQ邮箱授权码获取

1. 登录QQ邮箱
2. 设置 -> 账户 -> POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务
3. 开启 SMTP 服务
4. 生成授权码
5. 将授权码填入 `your_qq_email_auth_code`

#### 5.3 数据库配置

将以下配置替换为你的实际数据库信息：
- `your_mysql_username`: 你的MySQL用户名
- `your_mysql_password`: 你的MySQL密码

#### 5.4 Redis配置

将 `your_redis_password` 替换为你的Redis密码。如果Redis没有设置密码，可以留空或删除该配置项。

### 6. 启动服务

```bash
# 启动网关服务
cd forum-application
./mvnw spring-boot:run

# 启动用户服务
cd forum-user
./mvnw spring-boot:run

# 启动帖子服务
cd forum-post
./mvnw spring-boot:run

# 启动评论服务
cd forum-comment
./mvnw spring-boot:run
```

或者在项目根目录执行：

```bash
./mvnw clean install
./mvnw spring-boot:run -pl forum-application
```

### 7. 使用Docker Compose启动

```bash
docker-compose up -d
```

这将启动MySQL、Redis和Nacos服务。

## API访问

所有API通过网关访问：`http://localhost:9090/api/`

- 用户服务: `http://localhost:9090/api/user/`
- 帖子服务: `http://localhost:9090/api/post/`
- 评论服务: `http://localhost:9090/api/comment/`

## 监控

- Sentinel Dashboard: `http://localhost:8858`
- Nacos Console: `http://localhost:8848/nacos`

## 安全说明

**重要**:
- 切勿将包含真实密码、密钥的配置文件提交到版本控制系统
- 生产环境请使用强密码和复杂的JWT密钥
- 建议使用环境变量或密钥管理服务来管理敏感配置
- 定期更换JWT密钥和数据库密码

## 项目结构

```
forum-backend/
├── forum-application/    # API网关
├── forum-user/          # 用户服务
├── forum-post/          # 帖子服务
├── forum-comment/       # 评论服务
├── forum-common/        # 公共模块
├── sql/                 # 数据库脚本
├── docker-compose.yml   # Docker编排文件
└── pom.xml             # 父POM文件
```

## 常见问题

### Q: Nacos连接失败
A: 检查Nacos是否正常启动，确认地址为 `localhost:8848`

### Q: 服务注册失败
A: 检查application.yml中的Nacos配置，确保服务名正确

### Q: 邮件发送失败
A: 检查QQ邮箱授权码是否正确，确认SMTP服务已开启

## License

MIT License
