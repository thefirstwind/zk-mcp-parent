# 局域网网络配置指南

## 概述

本文档说明如何配置 `zkInfo` 和 `mcp-router-v3` 项目以在局域网环境中连接 MySQL、Nacos 和 ZooKeeper。

## 环境变量配置

### 方式一：使用环境变量（推荐）

在启动应用前设置环境变量：

```bash
# MySQL 配置
export MYSQL_HOST=192.168.0.101
export MYSQL_PORT=3306
export MYSQL_DATABASE=mcp_bridge
export MYSQL_USERNAME=mcp_user
export MYSQL_PASSWORD=mcp_user

# Nacos 配置
export NACOS_SERVER_ADDR=192.168.0.101:8848
export NACOS_NAMESPACE=public
export NACOS_USERNAME=nacos
export NACOS_PASSWORD=nacos

# ZooKeeper 配置
export ZK_CONNECT_STRING=192.168.0.101:2181

# 启动应用
java -jar zkInfo.jar
```

### 方式二：使用 .env 文件

创建 `.env` 文件（需要配合 `dotenv` 工具使用）：

```bash
MYSQL_HOST=192.168.0.101
MYSQL_PORT=3306
MYSQL_DATABASE=mcp_bridge
MYSQL_USERNAME=mcp_user
MYSQL_PASSWORD=mcp_user

NACOS_SERVER_ADDR=192.168.0.101:8848
NACOS_NAMESPACE=public
NACOS_USERNAME=nacos
NACOS_PASSWORD=nacos

ZK_CONNECT_STRING=192.168.0.101:2181
```

### 方式三：直接修改 application.yml

如果不想使用环境变量，可以直接修改 `application.yml` 文件中的默认值。

## 网络连通性检查

### 1. 检查 MySQL 连接

```bash
# 测试 MySQL 连接
mysql -h 192.168.0.101 -P 3306 -u mcp_user -p

# 或使用 telnet
telnet 192.168.0.101 3306
```

### 2. 检查 Nacos 连接

```bash
# 测试 Nacos HTTP 接口
curl http://192.168.0.101:8848/nacos/v1/console/health

# 或使用浏览器访问
http://192.168.0.101:8848/nacos
```

### 3. 检查 ZooKeeper 连接

```bash
# 使用 telnet 测试
telnet 192.168.0.101 2181

# 或使用 zkCli.sh
./zkCli.sh -server 192.168.0.101:2181
```

## 防火墙配置

确保以下端口在服务器防火墙中开放：

- **MySQL**: 3306
- **Nacos**: 8848 (HTTP), 9848 (gRPC)
- **ZooKeeper**: 2181

### Linux 防火墙配置示例

```bash
# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=3306/tcp
sudo firewall-cmd --permanent --add-port=8848/tcp
sudo firewall-cmd --permanent --add-port=2181/tcp
sudo firewall-cmd --reload

# Ubuntu/Debian
sudo ufw allow 3306/tcp
sudo ufw allow 8848/tcp
sudo ufw allow 2181/tcp
sudo ufw reload
```

## 服务端配置

### MySQL 配置

确保 MySQL 允许远程连接：

1. 修改 `my.cnf` 或 `my.ini`：
```ini
[mysqld]
bind-address = 0.0.0.0  # 允许所有 IP 连接，或指定具体 IP
```

2. 创建远程用户并授权：
```sql
CREATE USER 'mcp_user'@'%' IDENTIFIED BY 'mcp_user';
GRANT ALL PRIVILEGES ON mcp_bridge.* TO 'mcp_user'@'%';
FLUSH PRIVILEGES;
```

### Nacos 配置

修改 `nacos/conf/application.properties`：

```properties
# 允许外部访问
server.address=0.0.0.0
server.port=8848
```

### ZooKeeper 配置

确保 `zoo.cfg` 中的配置正确：

```properties
clientPort=2181
# 如果需要外部访问，确保监听地址正确
```

## 常见问题排查

### 1. 连接超时

- 检查防火墙是否开放端口
- 检查服务是否正在运行
- 检查 IP 地址是否正确

### 2. 认证失败

- 检查用户名和密码是否正确
- 检查用户是否有远程访问权限（MySQL）
- 检查 Nacos 用户名密码是否正确

### 3. 网络不通

- 使用 `ping` 检查网络连通性
- 使用 `telnet` 检查端口是否开放
- 检查路由表和网络配置

## 配置示例

### Docker Compose 环境变量示例

```yaml
version: '3.8'
services:
  zkinfo:
    image: zkinfo:latest
    environment:
      - MYSQL_HOST=192.168.0.101
      - MYSQL_PORT=3306
      - MYSQL_DATABASE=mcp_bridge
      - MYSQL_USERNAME=mcp_user
      - MYSQL_PASSWORD=mcp_user
      - NACOS_SERVER_ADDR=192.168.0.101:8848
      - ZK_CONNECT_STRING=192.168.0.101:2181
```

### Kubernetes ConfigMap 示例

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: zkinfo-config
data:
  MYSQL_HOST: "192.168.0.101"
  MYSQL_PORT: "3306"
  MYSQL_DATABASE: "mcp_bridge"
  NACOS_SERVER_ADDR: "192.168.0.101:8848"
  ZK_CONNECT_STRING: "192.168.0.101:2181"
```

## 注意事项

1. **安全性**：生产环境建议使用强密码，并限制 IP 访问范围
2. **性能**：局域网连接通常比本地连接稍慢，注意超时配置
3. **监控**：建议配置连接池监控和健康检查
4. **备份**：定期备份数据库配置和连接信息

