# 推送项目到 GitHub 快速指南

## 📋 当前状态

✅ 已完成：
- 初始化 Git 仓库
- 创建初始提交（115 个文件）
- 创建 `main` 和 `develop` 分支
- 完善 `.gitignore` 文件

📍 当前分支结构：
```
* main (5017ae7) - 初始提交：Spring Boot 论坛应用
  └── develop (b3ba612) - 添加 Git 工作流程指南
```

---

## 🚀 推送到 GitHub 的步骤

### 第一步：在 GitHub 创建仓库

1. 打开浏览器访问：https://github.com
2. 点击右上角的 `+` 按钮，选择 `New repository`
3. 填写仓库信息：
   - **Repository name**: `forum-backend`（或你喜欢的名字）
   - **Description**: `Spring Boot 论坛后端项目（单体应用，计划迁移到微服务）`
   - **可见性**: 选择 `Private`（私有）或 `Public`（公开）
   - ⚠️ **重要**：不要勾选以下任何选项：
     - ❌ Add a README file
     - ❌ Add .gitignore
     - ❌ Choose a license

4. 点击 `Create repository` 按钮

### 第二步：关联远程仓库

创建完成后，GitHub 会显示一个页面，找到 **"…or push an existing repository from the command line"** 部分。

在项目目录执行以下命令：

```bash
# 1. 添加远程仓库（替换 YOUR_USERNAME 为你的 GitHub 用户名）
git remote add origin https://github.com/YOUR_USERNAME/forum-backend.git

# 2. 验证远程仓库已添加
git remote -v

# 应该看到：
# origin  https://github.com/YOUR_USERNAME/forum-backend.git (fetch)
# origin  https://github.com/YOUR_USERNAME/forum-backend.git (push)
```

### 第三步：推送代码

```bash
# 1. 推送 main 分支（主分支）
git push -u origin main

# 2. 推送 develop 分支（开发分支）
git push -u origin develop

# 3. 查看所有分支（包括远程）
git branch -a
```

**第一次推送时会要求认证：**
- **HTTPS 方式**：输入 GitHub 用户名和密码
  - 注意：密码需要使用 Personal Access Token（PAT）
  - 创建 PAT：GitHub → Settings → Developer settings → Personal access tokens → Generate new token

- **SSH 方式**（推荐，无需每次输入密码）：
  ```bash
  # 生成 SSH 密钥
  ssh-keygen -t ed25519 -C "degulasihanyu@gmail.com"

  # 查看公钥
  cat ~/.ssh/id_ed25519.pub

  # 复制公钥内容，添加到 GitHub：
  # GitHub → Settings → SSH and GPG keys → New SSH key

  # 修改远程仓库地址为 SSH
  git remote set-url origin git@github.com:YOUR_USERNAME/forum-backend.git
  ```

### 第四步：在 GitHub 上查看代码

1. 刷新 GitHub 仓库页面
2. 应该能看到所有代码已上传
3. 点击左上角分支切换按钮，确认 `main` 和 `develop` 都已推送

### 第五步：设置默认分支（可选）

建议将 `develop` 设为默认分支，这样新的 Pull Request 会默认合并到这里：

1. GitHub 仓库页面 → `Settings`
2. 左侧菜单 → `Branches`
3. Default branch → 点击切换按钮
4. 选择 `develop` → `Update`

---

## 🔧 常见问题

### Q1: 推送时提示 "Authentication failed"

**原因**：GitHub 已停止支持密码认证，需要使用 Personal Access Token。

**解决方案**：
1. 访问：GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token
2. 勾选权限：`repo`（完整的仓库访问权限）
3. 生成后复制 Token（只显示一次！）
4. 推送时使用 Token 作为密码

或者改用 SSH 方式（见上文）。

### Q2: 推送时提示 "fatal: repository not found"

**原因**：远程仓库地址错误或没有权限。

**解决方案**：
```bash
# 检查远程仓库地址
git remote -v

# 删除错误的远程仓库
git remote remove origin

# 重新添加正确的地址
git remote add origin https://github.com/你的用户名/仓库名.git
```

### Q3: 推送时提示 "Updates were rejected"

**原因**：远程仓库有本地没有的提交（可能是在 GitHub 网页上直接编辑了文件）。

**解决方案**：
```bash
# 方案 1：拉取并合并（推荐）
git pull origin main --allow-unrelated-histories
git push origin main

# 方案 2：强制推送（危险！会覆盖远程）
git push -f origin main  # 仅在确定本地版本正确时使用
```

### Q4: 如何更新远程仓库？

```bash
# 1. 在本地修改代码
# ... 修改文件 ...

# 2. 查看修改
git status
git diff

# 3. 提交
git add .
git commit -m "feat: 添加某某功能"

# 4. 推送
git push origin develop  # 推送到 develop 分支
```

---

## 📚 后续工作建议

### 1. 创建 README.md

```bash
# 切换到 develop 分支
git checkout develop

# 创建 README（建议参考 CLAUDE.md 的内容）
# 编辑器创建 README.md 文件...

# 提交
git add README.md
git commit -m "docs: 添加项目 README 文档"
git push origin develop
```

### 2. 添加项目说明文件

建议添加以下文件（在 develop 分支上）：
- `README.md` - 项目介绍和快速开始
- `CONTRIBUTING.md` - 贡献指南（如果开源）
- `CHANGELOG.md` - 版本更新记录
- `LICENSE` - 开源协议（如果开源）

### 3. 保护主分支

在 GitHub 上设置分支保护规则：
1. Settings → Branches → Add rule
2. Branch name pattern: `main`
3. 勾选：
   - ✅ Require a pull request before merging
   - ✅ Require approvals (设置需要几个人审查)
   - ✅ Require status checks to pass before merging
4. 这样 main 分支就只能通过 PR 合并，防止直接推送

### 4. 设置 GitHub Actions（可选）

自动化测试和部署：

`.github/workflows/ci.yml`:
```yaml
name: CI

on:
  push:
    branches: [ develop, main ]
  pull_request:
    branches: [ develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Build with Maven
      run: ./mvnw clean package
    - name: Run tests
      run: ./mvnw test
```

---

## 🎯 下一步：开始开发

### 开发新功能的标准流程

```bash
# 1. 确保在最新的 develop 分支上
git checkout develop
git pull origin develop

# 2. 创建功能分支
git checkout -b feature/功能名称

# 3. 开发并提交
# ... 编写代码 ...
git add .
git commit -m "feat: 功能描述"

# 4. 推送功能分支
git push -u origin feature/功能名称

# 5. 在 GitHub 创建 Pull Request
# 访问仓库页面，点击 "Compare & pull request"
# base: develop ← compare: feature/功能名称
# 填写 PR 描述，提交审查

# 6. 审查通过后合并到 develop
# 在 GitHub 上点击 "Merge pull request"

# 7. 删除功能分支（可选）
git checkout develop
git pull origin develop
git branch -d feature/功能名称
git push origin --delete feature/功能名称
```

---

## 📖 延伸阅读

完成推送后，建议阅读以下文档：
1. **GIT_WORKFLOW.md** - 详细的 Git 工作流程（已在项目中）
2. **CLAUDE.md** - 项目架构和技术文档
3. [GitHub 官方文档](https://docs.github.com/cn)

---

## ✅ 检查清单

推送完成后，确认以下事项：

- [ ] GitHub 仓库中能看到所有代码
- [ ] `main` 和 `develop` 两个分支都已推送
- [ ] 仓库的默认分支是 `develop`
- [ ] 可以在 GitHub 上浏览代码和提交历史
- [ ] 远程仓库地址已正确配置（`git remote -v`）
- [ ] 已阅读 `GIT_WORKFLOW.md` 了解日常开发流程

全部完成后，你就可以开始正常的 Git 工作流程了！

---

**有任何问题，参考 GIT_WORKFLOW.md 或者询问 AI 助手。**
