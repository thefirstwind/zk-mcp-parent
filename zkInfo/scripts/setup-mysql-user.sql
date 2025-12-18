-- ============================================
-- MySQL 用户和数据库设置脚本
-- 使用方法：mysql -u root -p < setup-mysql-user.sql
-- ============================================

-- 1. 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS mcp_bridge 
    DEFAULT CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- 2. 删除所有现有的 mcp_user 用户（避免冲突）
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

-- 6. 显示创建结果
SELECT 'User created successfully!' AS Status;
SELECT User, Host FROM mysql.user WHERE User = 'mcp_user';
SELECT 'Grants for mcp_user@%:' AS Info;
SHOW GRANTS FOR 'mcp_user'@'%';

