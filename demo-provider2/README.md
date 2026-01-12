# Demo Provider 2 (Alibaba Dubbo 2.5)

这是一个使用 Alibaba Dubbo 2.5 版本的演示服务提供者项目。

## 项目说明

- **Dubbo 版本**: Alibaba Dubbo 2.5.10
- **Spring Boot 版本**: 2.7.18
- **Java 版本**: 17

## 与 demo-provider 的主要区别

1. **Dubbo 版本**: 使用 Alibaba Dubbo 2.5 而不是 Apache Dubbo 3.x
2. **Spring Boot 版本**: 使用 Spring Boot 2.7.18 而不是 3.x
3. **注解**: 使用 `com.alibaba.dubbo.config.annotation.Service` 而不是 `org.apache.dubbo.config.annotation.DubboService`
4. **Group 支持**: Dubbo 2.5 不支持 group 参数，所有服务实现类都移除了 group 配置
5. **端口**: 使用不同的端口避免冲突
   - HTTP 端口: 8084
   - Dubbo 协议端口: 20884

## 服务列表

项目提供以下三个服务：

1. **UserService** - 用户服务
2. **OrderService** - 订单服务
3. **ProductService** - 产品服务

## 配置说明

### application.yml

```yaml
server:
  port: 8084

dubbo:
  application:
    name: demo-provider2
  registry:
    address: zookeeper://localhost:2181
  protocol:
    name: dubbo
    port: 20884
    serialization: hessian2
```

### 服务实现

所有服务实现类都使用 `@Service` 注解：

```java
@Service(version = "1.0.0", interfaceClass = UserService.class)
public class UserServiceImpl implements UserService {
    // ...
}
```

**注意**: Dubbo 2.5 不支持 `group` 参数，所以注解中不包含 `group` 配置。

## 启动项目

1. 确保 ZooKeeper 已启动（默认端口 2181）
2. 运行以下命令：

```bash
mvn spring-boot:run
```

或者：

```bash
java -jar target/demo-provider2-1.0.0.jar
```

## 注意事项

1. **Dubbo 2.5 不支持 group**: 所有服务都不使用 group 参数
2. **序列化**: 强制使用 hessian2 序列化，确保与消费者兼容
3. **端口冲突**: 确保端口 8084 和 20884 未被占用
4. **ZooKeeper**: 确保 ZooKeeper 服务正常运行

## 与 zkInfo 的兼容性

zkInfo 项目已经支持 Dubbo 2.5 的兼容性处理：
- 自动检测 Dubbo 版本
- 对于不支持 group 的版本（如 2.5），自动移除 group 参数
- 支持泛化调用

## 测试

启动后，可以通过 zkInfo 项目进行服务发现和调用测试。



