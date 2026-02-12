package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.wizard.DubboInterfaceInfo;
import com.pajk.mcpmetainfo.core.model.wizard.MethodInfo;
import com.pajk.mcpmetainfo.core.model.wizard.ParameterInfo;
import com.pajk.mcpmetainfo.core.model.wizard.PomParseProgress;
import lombok.extern.slf4j.Slf4j;
import org.objectweb.asm.*;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * JAR 包扫描服务
 * 使用 ASM 扫描 JAR 包中的 class 文件，提取 Dubbo 接口信息
 */
@Slf4j
@Service
public class JarScannerService {
    
    /**
     * 扫描 JAR 包并提取 Dubbo 接口
     *
     * @param jarFile JAR 文件
     * @param jarName JAR 文件名（用于日志）
     * @param progress 进度对象
     * @param progressCallback 进度回调
     * @return Dubbo 接口列表
     */
    public List<DubboInterfaceInfo> scanJarForDubboInterfaces(
            File jarFile,
            String jarName,
            PomParseProgress progress,
            Consumer<PomParseProgress> progressCallback
    ) {
        List<DubboInterfaceInfo> interfaces = new ArrayList<>();
        
        try (JarFile jar = new JarFile(jarFile)) {
            progress.addLog(String.format("正在扫描 JAR: %s", jarName));
            progressCallback.accept(progress);
            
            // 遍历 JAR 包中的所有 class 文件
            jar.stream()
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().contains("$")) // 排除内部类
                    .forEach(entry -> {
                        try {
                            DubboInterfaceInfo interfaceInfo = analyzeClassFile(jar, entry, jarName);
                            if (interfaceInfo != null) {
                                interfaces.add(interfaceInfo);
                                progress.addLog(String.format("  ✅ 发现接口: %s (%d 个方法)",
                                        interfaceInfo.getInterfaceName(),
                                        interfaceInfo.getMethods().size()));
                                progressCallback.accept(progress);
                            }
                        } catch (Exception e) {
                            log.debug("分析 class 文件失败: {} - {}", entry.getName(), e.getMessage());
                        }
                    });
            
            if (interfaces.isEmpty()) {
                progress.addLog(String.format("  ⚠️ 未在 %s 中发现 Dubbo 接口", jarName));
            } else {
                progress.addLog(String.format("✅ 从 %s 中提取了 %d 个接口", jarName, interfaces.size()));
            }
            progressCallback.accept(progress);
            
        } catch (Exception e) {
            log.error("扫描 JAR 失败: {}", jarName, e);
            progress.addLog(String.format("❌ 扫描失败: %s - %s", jarName, e.getMessage()));
            progressCallback.accept(progress);
        }
        
        return interfaces;
    }
    
    /**
     * 分析单个 class 文件
     *
     * @param jar JAR 文件
     * @param entry JAR 条目
     * @param jarName JAR 文件名
     * @return Dubbo 接口信息，如果不是接口则返回 null
     */
    private DubboInterfaceInfo analyzeClassFile(JarFile jar, JarEntry entry, String jarName) throws Exception {
        try (InputStream is = jar.getInputStream(entry)) {
            ClassReader classReader = new ClassReader(is);
            
            // 使用 ASM Visitor 分析类
            DubboInterfaceVisitor visitor = new DubboInterfaceVisitor();
            // 重要：不能使用 SKIP_DEBUG，否则会跳过参数名信息！
            // 参数名信息存储在 MethodParameters 属性中（需要 -parameters 编译）
            classReader.accept(visitor, 0);  // 使用 0 以读取所有信息包括参数名
            
            // 只处理接口
            if (visitor.isInterface()) {
                String className = visitor.getClassName();
                List<MethodInfo> methods = visitor.getMethods();
                
                // 只返回有方法的接口
                if (!methods.isEmpty()) {
                    return DubboInterfaceInfo.builder()
                            .interfaceName(className)
                            .simpleClassName(getSimpleClassName(className))
                            .jarName(jarName)
                            .methods(methods)
                            .build();
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取简单类名
     */
    private String getSimpleClassName(String fullClassName) {
        int lastDot = fullClassName.lastIndexOf('.');
        return lastDot > 0 ? fullClassName.substring(lastDot + 1) : fullClassName;
    }
    
    /**
     * ASM ClassVisitor 用于提取接口信息
     */
    private static class DubboInterfaceVisitor extends ClassVisitor {
        private boolean isInterface = false;
        private String className;
        private final List<MethodInfo> methods = new ArrayList<>();
        
        public DubboInterfaceVisitor() {
            super(Opcodes.ASM9);
        }
        
        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            // 检查是否是接口
            this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            this.className = name.replace('/', '.');
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                          String signature, String[] exceptions) {
            // 只处理公共方法，排除特殊方法
            if ((access & Opcodes.ACC_PUBLIC) != 0 &&
                    !name.equals("<init>") &&
                    !name.equals("<clinit>")) {
                
                return new MethodVisitor(Opcodes.ASM9) {
                    private final List<String> parameterNames = new ArrayList<>();
                    
                    @Override
                    public void visitParameter(String paramName, int paramAccess) {
                        // 从 Parameter 注解或 -parameters 编译选项获取参数名
                        if (paramName != null && !paramName.isEmpty()) {
                            parameterNames.add(paramName);
                        }
                    }
                    
                    @Override
                    public void visitLocalVariable(String varName, String varDescriptor,
                                                     String varSignature, Label start, Label end, int index) {
                        // 从 LocalVariableTable 获取参数名
                        // index 0 是 'this' (对于实例方法)，参数从 index 1 开始
                        // 但对于接口方法（抽象方法），没有 'this'
                        if (index > 0 && (parameterNames.size() < index)) {
                            // 只添加参数变量，不添加局部变量
                            Type methodType = Type.getMethodType(descriptor);
                            if (index <= methodType.getArgumentTypes().length) {
                                parameterNames.add(varName);
                            }
                        } else if (index == 0 && !varName.equals("this")) {
                            // 接口方法的第一个参数（没有this）
                            parameterNames.add(varName);
                        }
                    }
                    
                    @Override
                    public void visitEnd() {
                        // 解析方法描述符
                        Type methodType = Type.getMethodType(descriptor);
                        Type returnType = methodType.getReturnType();
                        Type[] argumentTypes = methodType.getArgumentTypes();
                        
                        // 创建参数列表
                        List<ParameterInfo> params = new ArrayList<>();
                        for (int i = 0; i < argumentTypes.length; i++) {
                            Type argType = argumentTypes[i];
                            
                            // 尝试使用真实参数名
                            String paramName;
                            if (i < parameterNames.size() && parameterNames.get(i) != null) {
                                paramName = parameterNames.get(i);
                                log.debug("✅ 使用真实参数名: {}.{}({}) -> {}", 
                                    className, name, i, paramName);
                            } else {
                                // 回退到默认参数名
                                paramName = "arg" + i;
                                log.warn("⚠️ 无法获取参数名，使用默认: {}.{}({}) -> {}", 
                                    className, name, i, paramName);
                            }
                            
                            params.add(ParameterInfo.builder()
                                    .name(paramName)
                                    .type(argType.getClassName())
                                    .typeSimpleName(getSimpleTypeName(argType.getClassName()))
                                    .required(true)
                                    .build());
                        }
                        
                        // 添加方法信息
                        methods.add(MethodInfo.builder()
                                .methodName(name)
                                .returnType(returnType.getClassName())
                                .returnTypeSimpleName(getSimpleTypeName(returnType.getClassName()))
                                .parameters(params)
                                .selected(false)
                                .build());
                    }
                };
            }
            
            return null;
        }
        
        public boolean isInterface() {
            return isInterface;
        }
        
        public String getClassName() {
            return className;
        }
        
        public List<MethodInfo> getMethods() {
            return methods;
        }
        
        private String getSimpleTypeName(String fullTypeName) {
            // 处理数组类型
            if (fullTypeName.endsWith("[]")) {
                String baseType = fullTypeName.substring(0, fullTypeName.length() - 2);
                return getSimpleTypeName(baseType) + "[]";
            }
            
            // 处理泛型类型
            int genericStart = fullTypeName.indexOf('<');
            if (genericStart > 0) {
                fullTypeName = fullTypeName.substring(0, genericStart);
            }
            
            int lastDot = fullTypeName.lastIndexOf('.');
            return lastDot > 0 ? fullTypeName.substring(lastDot + 1) : fullTypeName;
        }
    }
}
