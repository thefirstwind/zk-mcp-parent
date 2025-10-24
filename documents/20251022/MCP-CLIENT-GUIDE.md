# 🌐 MCP客户端使用指南

## 📍 访问地址

在浏览器中打开: **http://localhost:9091/mcp-client.html**

## 🎯 功能说明

### 1. 工具列表 (Tools List)
- 点击"🔄 刷新工具列表"查看所有可用的MCP工具
- 显示每个工具的状态（在线/离线）、提供者信息

### 2. JSON-RPC调用
- 选择方法（initialize、tools/list、tools/call、ping）
- 输入参数（JSON格式）
- 点击"📤 发送请求"
- 查看响应结果

### 3. SSE流式调用 ⭐ 

#### 使用预设参数（推荐）
1. 在"预设参数"下拉框中选择一个选项，例如：
   - 👤 获取用户信息 (getUserById)
   - 👥 获取所有用户 (getAllUsers)
   - 📦 获取订单信息 (getOrderById)
   - 🛍️ 获取商品信息 (getProductById)

2. 选择后，工具名称和参数会自动填充

3. 点击"🌊 开始流式调用"

4. 观察日志窗口的实时数据流

5. 数据传输完成后会自动停止，或者手动点击"⏹️ 停止流式调用"

#### 自定义参数
1. 选择"🔧 自定义参数"
2. 手动输入工具名称，例如：
   ```
   com.zkinfo.demo.service.UserService.getUserById
   ```
3. 手动输入参数（JSON格式），例如：
   ```json
   {"args": [1]}
   ```
4. 点击"🌊 开始流式调用"

### 4. WebSocket
1. 点击"🔗 连接WebSocket"建立连接
2. 使用预设消息或输入自定义JSON-RPC消息
3. 点击"📤 发送消息"
4. 查看接收到的消息
5. 完成后点击"❌ 断开连接"

## 🔧 故障排查

### SSE连接显示"连接错误" ⚠️ 已修复！

**原因：** ~~浏览器可能缓存了旧版本的HTML文件~~ 此问题已在最新版本中修复！

新版本会正确区分：
- ✅ 正常完成：显示"✅ SSE数据传输完成，连接已关闭"
- ⚠️ 异常断开：显示"⚠️ SSE连接意外关闭（未收到完整数据）"
- ❌ 连接错误：显示"❌ SSE连接错误或断开"

**如果仍然看到旧的错误信息，请清除浏览器缓存：**
1. **硬刷新浏览器页面**（推荐）：
   - Windows/Linux: `Ctrl + Shift + R` 或 `Ctrl + F5`
   - macOS: `Cmd + Shift + R`

2. **清除浏览器缓存**：
   - Chrome: 设置 → 隐私和安全 → 清除浏览数据 → 缓存的图片和文件
   - Firefox: 选项 → 隐私与安全 → Cookies和网站数据 → 清除数据
   - Safari: 偏好设置 → 高级 → 显示开发菜单 → 开发 → 清空缓存

3. **使用隐私/无痕模式**：
   - 在浏览器中打开隐私浏览窗口（Ctrl+Shift+N 或 Cmd+Shift+N）
   - 访问 http://localhost:9091/mcp-client.html

4. **查看浏览器开发者工具**：
   - 按 `F12` 打开开发者工具
   - 查看 Console 标签页的错误信息
   - 查看 Network 标签页的请求详情

### 服务未运行

如果页面无法加载或工具列表为空，请确保zkInfo服务正在运行：

```bash
# 检查服务状态
curl http://localhost:9091/mcp/health

# 应该返回：
# {
#   "protocol": "MCP 2024-11-05",
#   "status": "UP",
#   "capabilities": ["tools", "streaming", "sse", "websocket"],
#   ...
# }
```

如果服务未运行，启动它：

```bash
cd zkInfo
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=9091"
```

## 📊 验证SSE功能

如果想快速验证SSE是否工作（不使用浏览器），可以运行：

```bash
bash /Users/shine/projects/zk-mcp-parent/test-sse-from-browser.sh
```

或者完整测试：

```bash
bash /Users/shine/projects/zk-mcp-parent/final-sse-test.sh
```

## 💡 示例场景

### 场景1：查询用户信息
1. 切换到"SSE流式调用"标签
2. 选择"👤 获取用户信息 (getUserById)"
3. 点击"🌊 开始流式调用"
4. 观察日志（新版本有更清晰的状态显示）：
   - ℹ️ "SSE连接已建立"
   - 📦 数据: {"message":"执行中...","progress":50}
   - 📦 数据: {用户详细信息}
   - ℹ️ "✅ SSE数据传输完成，连接已关闭"
   - ℹ️ "⏹️ 流式调用已停止"

### 场景2：查询所有商品
1. 切换到"SSE流式调用"标签
2. 选择"📦 获取所有商品 (getAllProducts)"
3. 点击"开始流式调用"
4. 观察日志中的商品列表流式返回

### 场景3：WebSocket实时通信
1. 切换到"WebSocket"标签
2. 点击"连接WebSocket"
3. 选择"📋 获取工具列表"
4. 点击"发送消息"
5. 查看接收到的工具列表
6. 尝试其他预设消息

## 🎨 日志说明

SSE日志窗口中的符号和颜色：
- ℹ️ 蓝色文字：信息性消息
  - "SSE连接已建立" - 连接成功
  - "✅ SSE数据传输完成，连接已关闭" - 正常完成
  - "⏹️ 流式调用已停止" - 手动停止
- 📦 绿色文字：接收到的数据
- ⚠️ 黄色文字：警告信息
  - "⚠️ SSE连接意外关闭（未收到完整数据）" - 连接异常断开
- ❌ 红色文字：错误信息
  - "❌ SSE连接错误或断开" - 连接失败

## 🔗 相关链接

- zkInfo服务: http://localhost:9091
- 健康检查: http://localhost:9091/mcp/health  
- MCP客户端: http://localhost:9091/mcp-client.html
- API文档: http://localhost:9091/swagger-ui.html (如果配置)

## ✨ 提示

1. **使用预设参数**更快速、更准确
2. **查看日志**了解每一步的执行情况
3. **SSE会自动关闭**当数据传输完成时
4. **WebSocket需要手动连接和断开**
5. **硬刷新浏览器**以获取最新版本的页面

---

🎉 享受使用MCP客户端的乐趣！

