# zkInfo 项目启动错误修复报告

**修复日期**: 2025-12-17  
**错误类型**: MyBatis Mapper XML 重复定义

---

## 🔴 错误描述

启动时出现以下错误：

```
Caused by: java.lang.IllegalArgumentException: Mapped Statements collection already contains key com.zkinfo.mapper.DubboServiceNodeMapper.findByServiceId. please check file [.../DubboServiceNodeMapper.xml] and file [.../DubboServiceNodeMapper.xml]
```

**根本原因**: `DubboServiceNodeMapper.xml` 文件中 `findByServiceId` 方法被定义了两次（第68行和第75行），导致 MyBatis 无法创建 `sqlSessionFactory`。

---

## ✅ 修复方案

### 修复文件
`src/main/resources/mybatis/mappers/DubboServiceNodeMapper.xml`

### 修复内容

**修复前**（有重复定义）:
```xml
<!-- 根据服务ID查找所有节点 -->
<select id="findByServiceId" parameterType="long" resultMap="DubboServiceNodeResultMap">
    SELECT <include refid="nodeColumns"/>
    FROM dubbo_service_nodes
    WHERE service_id = #{serviceId}
</select>

<!-- 根据服务ID查找节点 -->
<select id="findByServiceId" parameterType="long" resultMap="DubboServiceNodeResultMap">
    SELECT <include refid="nodeColumns"/>
    FROM dubbo_service_nodes
    WHERE service_id = #{serviceId}
</select>
```

**修复后**（删除重复定义）:
```xml
<!-- 根据服务ID查找所有节点 -->
<select id="findByServiceId" parameterType="long" resultMap="DubboServiceNodeResultMap">
    SELECT <include refid="nodeColumns"/>
    FROM dubbo_service_nodes
    WHERE service_id = #{serviceId}
</select>
```

---

## 📋 验证结果

1. ✅ **编译成功**: `mvn clean compile -DskipTests` 通过
2. ✅ **打包成功**: `mvn clean package -DskipTests` 通过
3. ✅ **MyBatis 配置**: 无重复定义错误

---

## 🔍 相关依赖链

错误影响的依赖链：
```
ZooKeeperService
  ↓ (依赖)
ProviderInfoDbService
  ↓ (依赖)
ProviderInfoMapper
  ↓ (需要)
sqlSessionTemplate
  ↓ (需要)
sqlSessionFactory
  ↓ (需要)
MyBatis Mapper XML 配置正确
```

---

## 📝 注意事项

1. **ProviderInfoDbService 不能删除**: 
   - 被 `ZooKeeperService`、`ApprovalController`、`ZkWatcherSchedulerService` 使用
   - 用于检查 Provider 的审批状态

2. **MyBatis Mapper XML 检查**:
   - 确保每个 Mapper 方法只定义一次
   - 避免重复的 `<select>`、`<insert>`、`<update>`、`<delete>` 标签

3. **编译验证**:
   - 修复后应重新编译和打包
   - 确保 MyBatis 配置正确

---

## 🚀 下一步

1. ✅ 已修复 `DubboServiceNodeMapper.xml` 的重复定义
2. ✅ 编译和打包成功
3. ⏭️ 可以尝试启动应用验证

---

## 📚 相关文件

- `src/main/resources/mybatis/mappers/DubboServiceNodeMapper.xml` - 已修复
- `src/main/java/com/zkinfo/service/ZooKeeperService.java` - 使用 ProviderInfoDbService
- `src/main/java/com/zkinfo/service/ProviderInfoDbService.java` - Provider 数据库服务

