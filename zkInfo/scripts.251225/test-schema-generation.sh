#!/bin/bash

# 测试 inputSchema 生成
# 直接测试 McpToolSchemaGenerator 的逻辑

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "测试 inputSchema 生成"
echo "=========================================="
echo ""

# 接口名
INTERFACE_NAME="com.zkinfo.demo.service.UserService"

# 测试方法
echo "📋 测试 1: getAllUsers (无参数方法)"
echo "----------------------------------------"
echo "接口: $INTERFACE_NAME"
echo "方法: getAllUsers"
echo ""

# 使用 Java 反射测试
cat > /tmp/TestSchema.java << 'JAVA_EOF'
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class TestSchema {
    public static void main(String[] args) {
        try {
            String interfaceName = args[0];
            String methodName = args[1];
            
            Class<?> interfaceClass = Class.forName(interfaceName);
            Method[] methods = interfaceClass.getMethods();
            
            Method targetMethod = null;
            for (Method method : methods) {
                if (method.getName().equals(methodName)) {
                    if (targetMethod == null || method.getParameterCount() < targetMethod.getParameterCount()) {
                        targetMethod = method;
                    }
                }
            }
            
            if (targetMethod != null) {
                Parameter[] parameters = targetMethod.getParameters();
                System.out.println("方法: " + targetMethod.getName());
                System.out.println("参数数量: " + parameters.length);
                
                if (parameters.length == 0) {
                    System.out.println("✅ 无参数方法 - 应该不需要 args");
                } else {
                    System.out.println("参数列表:");
                    for (int i = 0; i < parameters.length; i++) {
                        Parameter param = parameters[i];
                        System.out.println("  [" + i + "] " + param.getName() + " : " + param.getType().getSimpleName());
                    }
                }
            } else {
                System.out.println("❌ 方法未找到");
            }
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
JAVA_EOF

# 编译测试类
cd /Users/shine/projects.mcp-router-sse-parent/zk-mcp-parent/demo-provider
if [ -d "target/classes" ]; then
    CLASSPATH="target/classes"
else
    CLASSPATH="src/main/java"
fi

# 测试 getAllUsers
echo "测试 getAllUsers:"
javac -cp "$CLASSPATH" /tmp/TestSchema.java 2>&1 || echo "编译失败，需要先编译 demo-provider"
java -cp "/tmp:$CLASSPATH" TestSchema "$INTERFACE_NAME" "getAllUsers" 2>&1 || echo "执行失败"

echo ""
echo "📋 测试 2: getUserById (有参数方法)"
echo "----------------------------------------"
echo "接口: $INTERFACE_NAME"
echo "方法: getUserById"
echo ""

# 测试 getUserById
java -cp "/tmp:$CLASSPATH" TestSchema "$INTERFACE_NAME" "getUserById" 2>&1 || echo "执行失败"

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="

