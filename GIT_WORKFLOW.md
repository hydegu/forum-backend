# Git 工作流程指南

> 本文档专为从单体应用向微服务架构演进的项目设计，适用于多人协作开发

## 📚 目录

1. [为什么需要规范的 Git 工作流](#为什么需要规范的-git-工作流)
2. [分支策略](#分支策略)
3. [日常开发流程](#日常开发流程)
4. [微服务改造的 Git 策略](#微服务改造的-git-策略)
5. [常用命令速查](#常用命令速查)
6. [冲突解决](#冲突解决)
7. [最佳实践](#最佳实践)

---

## 为什么需要规范的 Git 工作流

### ❌ 粗暴的"拉取+上传"方式的问题

```bash
# 错误示范
git pull
# ... 修改代码 ...
git add .
git commit -m "更新"
git push
```

**这样做会导致：**
1. **代码覆盖**：多人同时修改时，后提交的会覆盖先提交的
2. **无法回滚**：出问题时不知道哪个版本是好的
3. **难以追溯**：提交信息不规范，无法快速定位问题
4. **冲突频繁**：没有隔离开发，主分支经常出问题
5. **无法并行开发**：不能同时进行多个功能开发

### ✅ 规范工作流的优势

1. **清晰的历史记录**：知道每个功能何时开发、由谁开发
2. **安全的主分支**：生产代码始终保持稳定
3. **并行开发**：多人可以同时开发不同功能
4. **方便回滚**：出问题可以快速回到之前的版本
5. **代码审查**：通过 Pull Request 进行代码审查

---

## 分支策略

我们采用 **Git Flow 改良版**，特别适合微服务改造：

```
main（生产环境）
  │
  ├─ develop（开发主分支）
  │    │
  │    ├─ feature/user-service（拆分用户服务）
  │    ├─ feature/post-service（拆分帖子服务）
  │    ├─ feature/comment-system（新功能开发）
  │    └─ refactor/service-layer（重构）
  │
  ├─ release/v1.0.0（发布准备）
  └─ hotfix/critical-bug（紧急修复）
```

### 分支类型说明

| 分支类型 | 命名规范 | 用途 | 生命周期 | 从哪里创建 | 合并到哪里 |
|---------|---------|------|---------|-----------|-----------|
| `main` | `main` | 生产环境代码 | 永久 | - | - |
| `develop` | `develop` | 开发主分支 | 永久 | `main` | - |
| `feature/*` | `feature/功能名` | 新功能开发 | 临时 | `develop` | `develop` |
| `refactor/*` | `refactor/重构内容` | 代码重构 | 临时 | `develop` | `develop` |
| `bugfix/*` | `bugfix/问题描述` | 普通 bug 修复 | 临时 | `develop` | `develop` |
| `release/*` | `release/v版本号` | 发布准备 | 临时 | `develop` | `main` + `develop` |
| `hotfix/*` | `hotfix/问题描述` | 紧急修复 | 临时 | `main` | `main` + `develop` |

---

## 日常开发流程

### 场景 1：开发新功能（推荐流程）

#### 第一步：从 develop 创建功能分支

```bash
# 1. 切换到 develop 分支
git checkout develop

# 2. 拉取最新代码
git pull origin develop

# 3. 创建并切换到新功能分支
git checkout -b feature/user-profile-page

# 查看当前分支
git branch
```

**为什么不直接在 develop 上开发？**
- develop 是共享分支，其他人也在用
- 功能分支可以随意提交，不影响他人
- 功能未完成时不会影响主分支

#### 第二步：开发并提交代码

```bash
# 1. 修改代码...

# 2. 查看修改了哪些文件
git status

# 3. 查看具体修改内容
git diff

# 4. 暂存文件（推荐分类暂存）
git add src/main/java/com/example/forum/controller/UserController.java
git add src/main/java/com/example/forum/service/UserService.java

# 5. 提交（写清楚提交信息）
git commit -m "feat: 添加用户个人资料页面接口

- 新增 GET /api/users/{id}/profile 接口
- 返回用户基本信息和发帖统计
- 添加缓存提升查询性能"

# 6. 继续开发，提交多次都可以
git add ...
git commit -m "feat: 添加用户资料编辑功能"
```

**提交信息规范（重要！）**

```
类型: 简短描述（50字以内）

详细说明：
- 做了什么
- 为什么这样做
- 注意事项

关联 Issue: #123
```

**常用类型：**
- `feat`: 新功能
- `fix`: 修复 bug
- `refactor`: 重构（不改变功能）
- `perf`: 性能优化
- `docs`: 文档更新
- `test`: 测试相关
- `chore`: 构建/工具配置

#### 第三步：推送到远程仓库

```bash
# 首次推送功能分支
git push -u origin feature/user-profile-page

# 后续推送（已建立跟踪关系）
git push
```

#### 第四步：合并回 develop

**方式 1：在 GitHub 上创建 Pull Request（推荐）**

1. 访问 GitHub 仓库
2. 点击 "Pull requests" → "New pull request"
3. 选择 `base: develop` ← `compare: feature/user-profile-page`
4. 填写 PR 描述，说明做了什么
5. 请求团队成员审查代码
6. 审查通过后，点击 "Merge pull request"

**方式 2：本地合并（单人开发可用）**

```bash
# 1. 切换到 develop
git checkout develop

# 2. 拉取最新代码
git pull origin develop

# 3. 合并功能分支
git merge feature/user-profile-page

# 4. 推送到远程
git push origin develop

# 5. 删除功能分支（可选）
git branch -d feature/user-profile-page
git push origin --delete feature/user-profile-page
```

---

### 场景 2：修复 Bug

```bash
# 1. 从 develop 创建 bugfix 分支
git checkout develop
git pull origin develop
git checkout -b bugfix/comment-not-showing

# 2. 修复 bug 并测试
# ... 修改代码 ...

# 3. 提交
git add .
git commit -m "fix: 修复评论不显示的问题

问题原因：rootId 为 null 时 SQL 查询失败
解决方案：修改 Mapper XML 中的判断条件

Fixes #456"

# 4. 推送并合并
git push -u origin bugfix/comment-not-showing
# 然后在 GitHub 上创建 PR 合并到 develop
```

---

### 场景 3：紧急修复生产环境问题

```bash
# 1. 从 main 创建 hotfix 分支
git checkout main
git pull origin main
git checkout -b hotfix/security-vulnerability

# 2. 修复问题
# ... 修改代码 ...

# 3. 提交
git commit -m "hotfix: 修复 JWT 令牌验证安全漏洞

严重性：高
影响范围：所有用户认证
解决方案：添加令牌过期时间验证"

# 4. 合并到 main（生产环境）
git checkout main
git merge hotfix/security-vulnerability
git tag v1.0.1  # 打标签记录版本
git push origin main --tags

# 5. 同步到 develop（避免下次发布丢失修复）
git checkout develop
git merge hotfix/security-vulnerability
git push origin develop

# 6. 删除 hotfix 分支
git branch -d hotfix/security-vulnerability
```

---

## 微服务改造的 Git 策略

### 阶段 1：准备阶段（当前项目）

```bash
# 当前分支结构
main (v1.0 - 单体应用)
  └── develop (日常开发)
```

### 阶段 2：拆分准备（建议使用 refactor 分支）

```bash
# 1. 创建微服务准备分支
git checkout develop
git checkout -b refactor/prepare-microservices

# 2. 重构代码为模块化结构
# - 明确服务边界
# - 拆分独立的 Service 模块
# - 定义服务间接口

# 3. 提交
git commit -m "refactor: 重构为模块化结构，准备微服务拆分

- 将 UserService 独立为模块
- 将 PostService 独立为模块
- 将 CommentService 独立为模块
- 定义各模块的 API 接口"

# 4. 合并回 develop
git push -u origin refactor/prepare-microservices
# 在 GitHub 创建 PR 合并到 develop
```

### 阶段 3：正式拆分（重要！使用新仓库）

**推荐方式：为每个微服务创建独立仓库**

```bash
# 主仓库（单体应用 - 保留作为参考）
forum-monolith/          # 原项目重命名

# 新的微服务仓库（建议结构）
forum-microservices/
  ├── user-service/      # 用户服务
  ├── post-service/      # 帖子服务
  ├── comment-service/   # 评论服务
  ├── auth-service/      # 认证服务
  ├── gateway/           # API 网关
  └── common/            # 共享模块
```

**创建微服务仓库的步骤：**

```bash
# 1. 在 GitHub 创建新仓库：forum-user-service

# 2. 本地创建项目目录
cd C:\Users\22417\Desktop\hy\Forum
mkdir microservices
cd microservices

# 3. 初始化用户服务
mkdir user-service
cd user-service
git init
git branch -M main

# 4. 从单体应用复制用户相关代码
# 复制 entity/AppUser.java
# 复制 service/UserService*.java
# 复制 controller/UserController.java
# 复制 repo/UserRepo.java

# 5. 创建 Spring Boot 微服务配置
# 添加 application.yml
# 添加 pom.xml（Spring Cloud 依赖）

# 6. 提交并推送
git add .
git commit -m "init: 初始化用户微服务

从单体应用拆分而来，包含：
- 用户认证功能
- 用户资料管理
- 用户关注系统"

git remote add origin https://github.com/你的用户名/forum-user-service.git
git push -u origin main
```

### 阶段 4：并行开发（单体 + 微服务）

在过渡期，两个项目会并存：

```bash
# 单体应用（维护模式）
cd C:\Users\22417\Desktop\hy\Forum\backcend
git checkout main
# 只接受 hotfix，不开发新功能

# 微服务（新功能开发）
cd C:\Users\22417\Desktop\hy\Forum\microservices\user-service
git checkout develop
git checkout -b feature/oauth2-login
# 开发新功能
```

---

## 常用命令速查

### 查看状态与历史

```bash
# 查看当前状态
git status

# 查看提交历史
git log --oneline --graph --all

# 查看某个文件的修改历史
git log --follow src/main/java/com/example/forum/service/UserService.java

# 查看某次提交的详细内容
git show 提交ID

# 查看分支列表
git branch -a  # 查看所有分支（包括远程）
```

### 撤销操作

```bash
# 撤销工作区的修改（还未 add）
git checkout -- 文件名

# 撤销暂存区的文件（已 add，未 commit）
git reset HEAD 文件名

# 撤销最后一次提交（保留修改）
git reset --soft HEAD^

# 撤销最后一次提交（丢弃修改，危险！）
git reset --hard HEAD^

# 修改最后一次提交信息
git commit --amend
```

### 分支操作

```bash
# 创建分支
git branch 分支名

# 切换分支
git checkout 分支名

# 创建并切换分支
git checkout -b 分支名

# 删除本地分支
git branch -d 分支名

# 删除远程分支
git push origin --delete 分支名

# 重命名分支
git branch -m 旧名字 新名字
```

### 远程操作

```bash
# 查看远程仓库
git remote -v

# 添加远程仓库
git remote add origin https://github.com/用户名/仓库名.git

# 拉取远程更新
git pull origin develop

# 推送到远程
git push origin 分支名

# 推送所有标签
git push --tags
```

---

## 冲突解决

### 什么是冲突？

当两个人修改了同一个文件的同一行时，Git 无法自动合并，就会产生冲突。

### 冲突场景示例

```bash
# 你在 feature/user-profile 分支修改了 UserService.java
git commit -m "添加用户资料缓存"

# 同时，队友在另一个分支也修改了同一文件
# 当你尝试合并时：
git checkout develop
git merge feature/user-profile

# 出现冲突提示：
# CONFLICT (content): Merge conflict in src/main/java/.../UserService.java
```

### 解决步骤

```bash
# 1. 查看冲突文件
git status

# 2. 打开冲突文件，会看到：
<<<<<<< HEAD
// develop 分支的代码
public User getUser(Long id) {
    return userRepo.selectById(id);
}
=======
// 你的分支的代码
public User getUser(Long id) {
    return cacheManager.get("user:" + id,
        () -> userRepo.selectById(id));
}
>>>>>>> feature/user-profile

# 3. 手动编辑，保留正确的代码（删除冲突标记）
public User getUser(Long id) {
    // 保留你的版本（带缓存）
    return cacheManager.get("user:" + id,
        () -> userRepo.selectById(id));
}

# 4. 标记为已解决
git add src/main/java/.../UserService.java

# 5. 完成合并
git commit -m "merge: 合并用户资料功能，保留缓存实现"
```

### 避免冲突的技巧

1. **频繁同步**：每天开始工作前 `git pull origin develop`
2. **小步提交**：每完成一个小功能就提交，不要积累太多
3. **模块化开发**：不同人开发不同模块，减少修改同一文件
4. **沟通协作**：多人修改同一文件时，提前沟通

---

## 最佳实践

### 1. 提交粒度

✅ **好的提交**：
```bash
git commit -m "feat: 添加用户头像上传功能"
git commit -m "feat: 添加头像图片压缩"
git commit -m "feat: 添加头像格式校验"
```

❌ **不好的提交**：
```bash
git commit -m "添加用户头像功能并修复了若干bug还优化了性能"
```

### 2. 提交信息模板

创建 `.gitmessage` 文件：

```
# 类型: 简短描述（不超过50字）

# 详细说明（可选）：
# - 为什么做这个修改
# - 修改了哪些内容
# - 注意事项

# 关联 Issue: #
# 影响范围:
```

配置使用：
```bash
git config commit.template .gitmessage
```

### 3. 分支命名规范

```bash
# 功能开发
feature/user-authentication
feature/post-search
feature/email-notification

# Bug 修复
bugfix/login-error
bugfix/comment-display

# 重构
refactor/service-layer
refactor/database-optimization

# 微服务拆分
refactor/split-user-service
refactor/extract-auth-module
```

### 4. 定期清理分支

```bash
# 查看已合并的分支
git branch --merged

# 删除已合并的本地分支
git branch -d feature/old-feature

# 删除远程分支
git push origin --delete feature/old-feature
```

### 5. 使用标签管理版本

```bash
# 创建标签（用于发布版本）
git tag -a v1.0.0 -m "正式版本 1.0.0 发布

功能列表：
- 用户注册登录
- 帖子发布评论
- 个人资料管理"

# 推送标签
git push origin v1.0.0

# 查看所有标签
git tag -l
```

### 6. 使用 .gitignore

已经配置好的忽略文件包括：
- `target/` - 编译输出
- `logs/` - 日志文件
- `uploads/` - 上传文件
- `.env` - 环境变量
- `*.log` - 日志文件

---

## 推送到 GitHub 的完整流程

### 第一步：在 GitHub 创建仓库

1. 访问 https://github.com
2. 点击右上角 `+` → `New repository`
3. 填写仓库名：`forum-backend`
4. 选择 **Private**（私有仓库）或 **Public**（公开）
5. **不要**勾选 "Initialize with README"（我们已有代码）
6. 点击 "Create repository"

### 第二步：关联远程仓库

```bash
# 添加远程仓库
git remote add origin https://github.com/你的用户名/forum-backend.git

# 查看远程仓库
git remote -v
```

### 第三步：推送代码

```bash
# 推送 main 分支
git push -u origin main

# 推送 develop 分支
git push -u origin develop

# 查看所有分支
git branch -a
```

### 第四步：设置默认分支

1. 在 GitHub 仓库页面，点击 "Settings"
2. 左侧菜单选择 "Branches"
3. 设置 Default branch 为 `develop`
4. 这样新的 PR 默认会合并到 develop

---

## 总结

### 核心原则

1. **main 分支永远是稳定的**：只接受经过测试的代码
2. **develop 是日常开发分支**：所有功能都先合并到这里
3. **功能分支独立开发**：每个功能一个分支，互不干扰
4. **提交信息要清晰**：方便追溯和回滚
5. **频繁拉取，小步提交**：减少冲突，便于审查

### 学习路线

1. **第 1 周**：熟悉基本命令（add, commit, push, pull）
2. **第 2 周**：掌握分支操作（branch, checkout, merge）
3. **第 3 周**：学习冲突解决和代码回滚
4. **第 4 周**：练习 Pull Request 和代码审查流程

### 参考资料

- [Git 官方文档（中文）](https://git-scm.com/book/zh/v2)
- [GitHub 快速入门](https://docs.github.com/cn/get-started)
- [Git Flow 工作流](https://nvie.com/posts/a-successful-git-branching-model/)

---

## 附录：团队协作示例

### 场景：三人团队开发论坛项目

**团队成员：**
- 小张：负责用户模块
- 小李：负责帖子模块
- 小王：负责评论模块

**协作流程：**

```bash
# 小张的工作流
git checkout develop
git pull origin develop
git checkout -b feature/user-follow-system
# ... 开发关注功能 ...
git push -u origin feature/user-follow-system
# 在 GitHub 创建 PR，请小李和小王审查

# 小李的工作流（同时进行）
git checkout develop
git pull origin develop
git checkout -b feature/post-search
# ... 开发搜索功能 ...
git push -u origin feature/post-search
# 在 GitHub 创建 PR

# 小王的工作流（同时进行）
git checkout develop
git pull origin develop
git checkout -b feature/comment-reactions
# ... 开发评论点赞功能 ...
git push -u origin feature/comment-reactions
# 在 GitHub 创建 PR

# 代码审查通过后，依次合并到 develop
# 三个功能互不干扰，可以并行开发
```

这样每个人都在自己的分支上工作，互不影响，最后统一合并到 develop 分支进行集成测试。