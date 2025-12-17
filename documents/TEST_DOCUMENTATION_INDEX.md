# 测试文档索引

**创建日期**: 2025-12-15  
**版本**: 1.0.0

---

## 📚 测试文档列表

### 1. TEST_GUIDE.md - 完整测试指南 ⭐
**文件大小**: 23KB  
**内容**: 详细的测试步骤和验证方法

**包含内容**:
- 测试环境准备
- 基础功能测试
- 三层过滤机制测试
- 项目管理功能测试
- 虚拟项目功能测试
- ZooKeeper监听优化测试
- Nacos注册功能测试
- 集成测试
- 性能测试
- 问题排查

**使用场景**: 完整的功能测试和验证

---

### 2. TEST_CHECKLIST.md - 测试检查清单
**文件大小**: 5KB  
**内容**: 测试项检查清单

**包含内容**:
- 基础功能测试清单
- 三层过滤机制测试清单
- 项目管理功能测试清单
- 虚拟项目功能测试清单
- ZooKeeper监听优化测试清单
- Nacos注册功能测试清单
- 集成测试清单
- 性能测试清单
- 测试记录模板

**使用场景**: 快速检查测试进度

---

### 3. quick-test.sh - 快速功能测试脚本
**文件大小**: 2.4KB  
**功能**: 快速验证基础API端点

**使用方法**:
```bash
cd zk-mcp-parent
bash quick-test.sh
```

**测试内容**:
- 健康检查
- 统计信息
- Provider列表
- 已注册服务
- 应用列表
- 接口列表
- API文档

---

### 4. api-test.sh - API功能测试脚本
**文件大小**: 5.6KB  
**功能**: 详细的API功能测试

**使用方法**:
```bash
cd zk-mcp-parent
bash api-test.sh
```

**测试内容**:
- 项目创建（如果API已实现）
- 项目列表查询
- 统计信息获取
- Provider列表获取
- 已注册服务查询
- 应用列表查询
- 接口列表查询
- ZooKeeper连接验证

---

## 🚀 快速开始

### 第一步: 环境准备
```bash
# 1. 检查依赖服务
nc -z localhost 2181 && echo "ZooKeeper OK" || echo "ZooKeeper未运行"
nc -z localhost 8848 && echo "Nacos OK" || echo "Nacos未运行"

# 2. 启动zkInfo服务
cd zk-mcp-parent/zkInfo
bash start-and-verify.sh
```

### 第二步: 快速验证
```bash
# 运行快速测试
cd zk-mcp-parent
bash quick-test.sh
```

### 第三步: 详细测试
```bash
# 运行API测试
bash api-test.sh

# 或按照测试指南逐步测试
# 参考: TEST_GUIDE.md
```

---

## 📋 测试流程建议

### 阶段1: 基础功能验证（5分钟）
1. 运行 `quick-test.sh` 验证基础API
2. 检查服务健康状态
3. 验证ZooKeeper连接

### 阶段2: 核心功能测试（30分钟）
1. 按照 `TEST_GUIDE.md` 第3节测试三层过滤机制
2. 按照 `TEST_GUIDE.md` 第4节测试项目管理功能
3. 使用 `TEST_CHECKLIST.md` 记录测试进度

### 阶段3: 高级功能测试（30分钟）
1. 按照 `TEST_GUIDE.md` 第5节测试虚拟项目功能
2. 按照 `TEST_GUIDE.md` 第6节测试ZooKeeper监听优化
3. 按照 `TEST_GUIDE.md` 第7节测试Nacos注册功能

### 阶段4: 集成测试（20分钟）
1. 按照 `TEST_GUIDE.md` 第8节进行集成测试
2. 验证与mcp-router-v3的集成
3. 端到端流程测试

### 阶段5: 性能测试（可选）
1. 按照 `TEST_GUIDE.md` 第9节进行性能测试
2. 验证性能优化效果

---

## 🔧 测试工具

### 命令行工具
- `curl` - HTTP请求测试
- `jq` - JSON解析（可选）
- `python3` - 脚本执行

### 浏览器工具
- Swagger UI: http://localhost:9091/swagger-ui.html
- Nacos控制台: http://localhost:8848/nacos (nacos/nacos)

### 日志查看
```bash
# zkInfo日志
tail -f zk-mcp-parent/zkInfo/logs/zkinfo.log

# demo-provider日志
tail -f zk-mcp-parent/demo-provider/logs/demo-provider.log

# mcp-router-v3日志
tail -f mcp-router-v3/logs/mcp-router-v3.log
```

---

## 📊 测试数据准备

### 测试项目数据
参考 `TEST_GUIDE.md` 第12节

### 测试服务数据
- UserService: `com.zkinfo.demo.service.UserService:1.0.0:demo`
- OrderService: `com.zkinfo.demo.service.OrderService:1.0.0:demo`
- ProductService: `com.zkinfo.demo.service.ProductService:1.0.0:demo`

---

## ⚠️ 注意事项

1. **API端点**: 部分API端点可能尚未实现，测试时会返回404，这是正常的
2. **服务启动顺序**: 建议先启动zkInfo，再启动demo-provider
3. **数据清理**: 测试完成后可以清理测试数据
4. **日志查看**: 遇到问题时，优先查看日志文件

---

## 🆘 获取帮助

1. **查看详细测试步骤**: `TEST_GUIDE.md`
2. **查看测试清单**: `TEST_CHECKLIST.md`
3. **查看问题排查**: `TEST_GUIDE.md` 第10节
4. **查看实现文档**: `DESIGN_COMPLIANCE_REPORT.md`

---

**最后更新**: 2025-12-15  
**维护者**: ZkInfo Team

