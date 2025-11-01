# 快速重置管理员密码指南

## 🎯 3步完成密码重置

### 步骤1：查看管理员信息

```powershell
cd C:\Users\22417\Desktop\hy\Forum\backcend
mysql -u root -p123456 forum_system < reset-admin-password.sql
```

这会显示所有用户，找到你的管理员用户名或ID。

---

### 步骤2：生成加密密码

**方法A：运行脚本**
```powershell
cd C:\Users\22417\Desktop\hy\Forum\backcend
.\generate-password.bat
```

**方法B：使用Maven**
```powershell
cd C:\Users\22417\Desktop\hy\Forum\backcend\forum-user
mvn exec:java -Dexec.mainClass="com.example.forum.user.utils.PasswordEncryptTool"
```

输出示例：
```
========================================
  密码加密工具
========================================

原始密码: 123321qq

加密后的字符串:
----------------------------------------
$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
----------------------------------------

SQL更新语句:
----------------------------------------
UPDATE users SET password = '$2a$10$xxx...' WHERE username = 'admin';
----------------------------------------
```

---

### 步骤3：更新数据库

**复制上面输出的SQL语句**，然后执行：

```powershell
mysql -u root -p123456 forum_system -e "UPDATE users SET password = '复制的加密字符串' WHERE username = 'admin';"
```

或者直接使用输出的完整SQL：
```powershell
mysql -u root -p123456 forum_system -e "UPDATE users SET password = '$2a$10$...(完整字符串)' WHERE username = 'admin';"
```

---

## 🔧 如果管理员用户名不是'admin'

### 先查询用户名
```powershell
mysql -u root -p123456 forum_system -e "SELECT id, username FROM users WHERE status='admin' OR role LIKE '%ADMIN%';"
```

### 然后替换用户名
```powershell
mysql -u root -p123456 forum_system -e "UPDATE users SET password = '加密字符串' WHERE username = '你的用户名';"
```

---

## 📝 完整示例

```powershell
# 1. 查看管理员用户
mysql -u root -p123456 forum_system -e "SELECT id, username FROM users;"

# 输出：
# +----+----------+
# | id | username |
# +----+----------+
# |  1 | admin    |
# |  2 | user1    |
# +----+----------+

# 2. 生成加密密码
cd C:\Users\22417\Desktop\hy\Forum\backcend
.\generate-password.bat

# 输出加密字符串，例如：
# $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

# 3. 更新密码
mysql -u root -p123456 forum_system -e "UPDATE users SET password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy' WHERE username = 'admin';"

# 4. 验证
mysql -u root -p123456 forum_system -e "SELECT username, password FROM users WHERE username = 'admin';"
```

---

## ⚠️ 注意事项

1. **BCrypt每次生成的加密字符串都不同**
   - 即使密码相同，加密结果也不同
   - 这是正常的，因为BCrypt会加入随机盐值

2. **加密字符串很长**
   - 通常是60个字符
   - 以`$2a$10$`或`$2b$10$`开头

3. **必须完整复制**
   - 不能有空格
   - 不能截断
   - 建议从SQL输出中直接复制完整的UPDATE语句

---

## 🎉 最简单的方法

如果你只想重置密码为 `123321qq`，直接运行：

```powershell
cd C:\Users\22417\Desktop\hy\Forum\backcend
.\generate-password.bat
```

然后：
1. 复制输出的"SQL更新语句"部分
2. 执行那条SQL
3. 完成！

密码重置工具已创建完成！🚀

