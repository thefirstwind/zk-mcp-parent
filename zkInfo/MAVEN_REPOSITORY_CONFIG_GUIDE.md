# Maven 私有仓库配置示例

## 本地开发环境配置 (application-local.yml)
```yaml
maven:
  # 使用 ~/.m2/settings.xml 中的配置
  use-settings-xml: true
```

## 生产环境配置 (application-prod.yml)
```yaml
maven:
  # 不使用 settings.xml，显式配置
  use-settings-xml: false
  
  nexus:
    url: ${NEXUS_URL:http://nexus.company.com/repository/maven-public/}
    username: ${NEXUS_USERNAME:build-user}
    password: ${NEXUS_PASSWORD}
    connect-timeout: 5000
    read-timeout: 30000
```

## 环境变量配置
```bash
# 设置环境变量
export NEXUS_URL="http://localhost:8881/repository/maven-public/"
export NEXUS_USERNAME="admin"
export NEXUS_PASSWORD="your-password"

# 启动应用
java -jar zkInfo.jar --spring.profiles.active=prod
```

## Docker 容器环境
```yaml
# docker-compose.yml
services:
  zkinfo:
    image: zkinfo:latest
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - NEXUS_URL=http://nexus:8881/repository/maven-public/
      - NEXUS_USERNAME=build-user
      - NEXUS_PASSWORD=secure-password
    networks:
      - backend

  nexus:
    image: sonatype/nexus3
    ports:
      - "8881:8081"
    networks:
      - backend
```

## Kubernetes 环境
```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: zkinfo-config
data:
  application-prod.yml: |
    maven:
      use-settings-xml: false
      nexus:
        url: http://nexus-service.default.svc.cluster.local/repository/maven-public/
        connect-timeout: 5000
        read-timeout: 30000

---
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: nexus-credentials
type: Opaque
stringData:
  username: build-user
  password: your-secure-password

---
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zkinfo
spec:
  template:
    spec:
      containers:
      - name: zkinfo
        image: zkinfo:latest
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: NEXUS_USERNAME
          valueFrom:
            secretKeyRef:
              name: nexus-credentials
              key: username
        - name: NEXUS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: nexus-credentials
              key: password
        volumeMounts:
        - name: config
          mountPath: /config
      volumes:
      - name: config
        configMap:
          name: zkinfo-config
```

## 配置优先级
1. **application.yml 中的 Nexus 配置**（生产环境推荐）
2. **~/.m2/settings.xml 中的镜像配置**（本地开发）
3. **没有配置时抛出异常**（强制要求配置）

## 安全最佳实践
1. ✅ 密码使用环境变量注入，不要硬编码
2. ✅ Kubernetes 使用 Secret 管理敏感信息
3. ✅ 生产环境禁用 settings.xml，使用明确配置
4. ✅ Nexus 配置只读权限账号（不需要deploy权限）
