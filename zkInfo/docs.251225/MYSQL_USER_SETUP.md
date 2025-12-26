# MySQL 用户配置指南

## 错误排查

### ERROR 1396: Operation CREATE USER failed

这个错误通常表示用户已经存在，但可能：
1. 用户已存在但主机不同（如 `mcp_user@localhost` vs `mcp_user@%`）
2. 用户已存在但需要先删除再创建
3. 权限不足

## 解决方案

### 方案一：检查并删除现有用户（推荐）

```sql
-- 1. 查看所有 mcp_user 用户
SELECT User, Host FROM mysql.user WHERE User = 'mcp_user';

-- 2. 删除所有现有的 mcp_user 用户（包括不同主机）
DROP USER IF EXISTS 'mcp_user'@'localhost';
DROP USER IF EXISTS 'mcp_user'@'127.0.0.1';
DROP USER IF EXISTS 'mcp_user'@'%';
DROP USER IF EXISTS 'mcp_user'@'192.168.0.101';

-- 3. 重新创建用户
CREATE USER 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';

-- 4. 授权
GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%';
FLUSH PRIVILEGES;

-- 5. 验证
SELECT User, Host FROM mysql.user WHERE User = 'mcp_user';
SHOW GRANTS FOR 'mcp_user'@'%';
```

### 方案二：直接授权（如果用户已存在）

```sql
-- 1. 查看现有用户
SELECT User, Host FROM mysql.user WHERE User = 'mcp_user';

-- 2. 如果用户存在但主机不同，先删除
DROP USER IF EXISTS 'mcp_user'@'localhost';
DROP USER IF EXISTS 'mcp_user'@'127.0.0.1';

-- 3. 创建新用户（如果不存在）
CREATE USER IF NOT EXISTS 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';

-- 4. 授权
GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%';
FLUSH PRIVILEGES;
```

### 方案三：使用 ALTER USER（MySQL 5.7.6+）

```sql
-- 如果用户已存在，可以修改密码和主机
ALTER USER 'mcp_user'@'localhost' IDENTIFIED BY 'mcp_user';
RENAME USER 'mcp_user'@'localhost' TO 'mcp_user'@'%';

-- 或者直接创建新用户
CREATE USER IF NOT EXISTS 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';
GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%';
FLUSH PRIVILEGES;
```

## 完整设置脚本

```sql
-- ============================================
-- MySQL 用户和数据库设置脚本
-- ============================================

-- 1. 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS mcp_bridge 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- 2. 删除所有现有的 mcp_user 用户
DROP USER IF EXISTS 'mcp_user'@'localhost';
DROP USER IF EXISTS 'mcp_user'@'127.0.0.1';
DROP USER IF EXISTS 'mcp_user'@'%';
DROP USER IF EXISTS 'mcp_user'@'192.168.0.101';

-- 3. 创建新用户（允许所有主机访问）
CREATE USER 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';

-- 4. 授权
GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%';

-- 5. 刷新权限
FLUSH PRIVILEGES;

-- 6. 验证
SELECT User, Host FROM mysql.user WHERE User = 'mcp_user';
SHOW GRANTS FOR 'mcp_user'@'%';

-- 7. 测试连接（在另一个终端执行）
-- mysql -h 192.168.0.101 -u mcp_user -p mcp_bridge
```

## 权限说明

### 最小权限配置（生产环境推荐）

如果只需要基本操作，可以使用最小权限：

```sql
-- 创建用户
CREATE USER 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';

-- 授予最小权限
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, INDEX, ALTER 
    ON mcp_bridge.* TO 'mcp_user'@'%';

FLUSH PRIVILEGES;
```

### 完整权限配置（开发环境）

```sql
-- 授予所有权限
GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%';
FLUSH PRIVILEGES;
```

## 常见问题

### 1. 用户已存在但无法连接

```sql
-- 检查用户是否存在
SELECT User, Host FROM mysql.user WHERE User = 'mcp_user';

-- 检查权限
SHOW GRANTS FOR 'mcp_user'@'%';

-- 重置密码
ALTER USER 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';
FLUSH PRIVILEGES;
```

### 2. 权限不足

确保使用 root 用户执行：

```sql
-- 检查当前用户
SELECT USER(), CURRENT_USER();

-- 如果当前用户不是 root，切换到 root
-- mysql -u root -p
```

### 3. 远程连接被拒绝

检查 MySQL 配置：

```sql
-- 检查 bind-address 配置
SHOW VARIABLES LIKE 'bind_address';

-- 如果显示 127.0.0.1，需要修改 my.cnf
-- bind-address = 0.0.0.0
-- 然后重启 MySQL 服务
```

### 4. 防火墙问题

确保防火墙开放 MySQL 端口：

```bash
# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=3306/tcp
sudo firewall-cmd --reload

# Ubuntu/Debian
sudo ufw allow 3306/tcp
sudo ufw reload
```

## 测试连接

创建用户后，测试连接：

```bash
# 从远程主机测试
mysql -h 192.168.0.101 -u mcp_user -p mcp_bridge

# 输入密码：mcp_user

# 如果连接成功，执行简单查询
mysql> SHOW TABLES;
mysql> SELECT DATABASE();
mysql> EXIT;
```

## 安全建议

1. **使用强密码**：生产环境建议使用复杂密码
2. **限制 IP 范围**：如果可以，使用具体 IP 而不是 `%`
   ```sql
   CREATE USER 'mcp_user'@'192.168.0.%' IDENTIFIED BY 'strong_password';
   ```
3. **定期审计**：定期检查用户权限
   ```sql
   SELECT User, Host FROM mysql.user;
   SHOW GRANTS FOR 'mcp_user'@'%';
   ```

