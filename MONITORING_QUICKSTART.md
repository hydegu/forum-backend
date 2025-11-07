# 监控系统快速启动指南

## 5 分钟快速上手

### 前置条件

- Java 17+
- Maven 3.6+
- Docker & Docker Compose
- MySQL 数据库
- Redis 服务

### 步骤 1: 启动应用

```bash
# 进入项目目录
cd C:\Users\22417\Desktop\hy\Forum\backcend

# 启动应用
./mvnw spring-boot:run
```

### 步骤 2: 验证 Actuator

访问以下端点确认监控已启用：

```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 应该返回:
# {"status":"UP"}

# 查看 Prometheus 指标
curl http://localhost:8080/actuator/prometheus
```

### 步骤 3: 启动监控栈

```bash
# 启动 Prometheus 和 Grafana
docker-compose -f docker-compose.monitoring.yml up -d

# 查看状态
docker-compose -f docker-compose.monitoring.yml ps
```

### 步骤 4: 访问监控界面

1. **Prometheus**: http://localhost:9090
   - 查看目标状态: Status -> Targets
   - 应该看到 `forum-app` 状态为 UP

2. **Grafana**: http://localhost:3000
   - 默认用户名/密码: `admin`/`admin`
   - 首次登录会要求修改密码

### 步骤 5: 导入仪表板

在 Grafana 中：

1. 点击 "+" -> "Import"
2. 输入仪表板 ID: `4701` (JVM Micrometer)
3. 选择数据源: Prometheus
4. 点击 "Import"

重复以上步骤导入其他仪表板：
- `6756` - Spring Boot Statistics
- `11378` - Spring Boot System Monitor

### 步骤 6: 查看业务指标

在 Grafana 中创建新面板，使用以下 PromQL 查询：

```promql
# 用户注册速率
rate(forum_user_registration_total[5m])

# 文章创建速率
rate(forum_post_created_total[5m])

# 缓存命中率
rate(forum_cache_hit_total[5m]) / (rate(forum_cache_hit_total[5m]) + rate(forum_cache_miss_total[5m]))
```

---

## 常见问题

### Q1: Prometheus 无法连接到应用

**解决方案**:
1. 检查应用是否正常运行: `curl http://localhost:8080/actuator/health`
2. 检查 Prometheus 配置: `monitoring/prometheus.yml`
3. 在 Docker 中使用 `host.docker.internal` 而不是 `localhost`

### Q2: Grafana 无法显示数据

**解决方案**:
1. 确认 Prometheus 数据源已正确配置
2. 在 Grafana 中测试数据源连接
3. 检查 Prometheus 是否成功抓取指标: http://localhost:9090/targets

### Q3: 日志文件在哪里？

日志文件位置：
- 主日志: `logs/forum.log`
- 错误日志: `logs/forum-error.log`
- JSON 日志: `logs/forum-json.log`

---

## 下一步

- 阅读完整文档: `docs/MONITORING_AND_LOGGING.md`
- 配置告警规则
- 优化数据库查询: `docs/DATABASE_OPTIMIZATION.md`
- 自定义 Grafana 仪表板

---

## 有用的命令

```bash
# 查看实时日志
tail -f logs/forum.log

# 查看 Prometheus 指标
curl http://localhost:8080/actuator/prometheus | grep forum_

# 停止监控栈
docker-compose -f docker-compose.monitoring.yml down

# 重启监控栈
docker-compose -f docker-compose.monitoring.yml restart

# 查看容器日志
docker logs forum-prometheus
docker logs forum-grafana
```

---

## 监控关键指标

定期检查以下指标：

1. **JVM 内存使用率**: 应 < 85%
2. **CPU 使用率**: 应 < 80%
3. **API 响应时间 (P95)**: 应 < 1s
4. **错误率**: 应 < 1%
5. **缓存命中率**: 应 > 70%

如果这些指标异常，请参考 `docs/MONITORING_AND_LOGGING.md` 进行故障排查。