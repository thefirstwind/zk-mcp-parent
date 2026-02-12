package com.pajk.mcpmetainfo.core.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;
import com.pajk.mcpmetainfo.core.model.wizard.DubboInterfaceInfo;
import com.pajk.mcpmetainfo.core.model.wizard.MethodInfo;
import com.pajk.mcpmetainfo.core.model.wizard.ParameterInfo;
import com.pajk.mcpmetainfo.core.model.wizard.PomParseProgress;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * JavaDoc 解析服务
 * 从 Java 源码中提取 JavaDoc 注释
 */
@Slf4j
@Service
public class JavaDocParserService {
    
    private final JavaParser javaParser = new JavaParser();
    
    /**
     * 解析 Git 仓库中的 Java 源码，提取 JavaDoc
     *
     * @param repoDirectory Git 仓库目录
     * @param interfaces 已提取的接口列表（从 JAR 扫描得到）
     * @param progress 进度对象
     * @param progressCallback 进度回调
     * @return 更新后的接口列表（包含 JavaDoc 信息）
     */
    public List<DubboInterfaceInfo> enrichWithJavaDoc(
            File repoDirectory,
            List<DubboInterfaceInfo> interfaces,
            PomParseProgress progress,
            Consumer<PomParseProgress> progressCallback
    ) throws IOException {
        
        progress.addLog("开始解析 JavaDoc...");
        progressCallback.accept(progress);
        
        // 查找所有 Java 源文件
        List<Path> javaFiles = findJavaFiles(repoDirectory.toPath());
        progress.addLog(String.format("找到 %d 个 Java 文件", javaFiles.size()));
        progressCallback.accept(progress);
        
        // 解析每个源文件，建立接口名到 JavaDoc 的映射
        Map<String, InterfaceJavaDoc> javadocMap = new HashMap<>();
        
        for (Path javaFile : javaFiles) {
            try {
                InterfaceJavaDoc doc = parseJavaFile(javaFile.toFile());
                if (doc != null && doc.isInterface()) {
                    javadocMap.put(doc.getFullyQualifiedName(), doc);
                }
            } catch (Exception e) {
                log.debug("解析 Java 文件失败: {} - {}", javaFile, e.getMessage());
            }
        }
        
        progress.addLog(String.format("解析了 %d 个接口的 JavaDoc", javadocMap.size()));
        progressCallback.accept(progress);
        
        // 匹配 JavaDoc 到已扫描的接口
        int enrichedCount = 0;
        for (DubboInterfaceInfo interfaceInfo : interfaces) {
            InterfaceJavaDoc javadoc = javadocMap.get(interfaceInfo.getInterfaceName());
            if (javadoc != null) {
                // 设置接口描述
                interfaceInfo.setDescription(javadoc.getDescription());
                
                // 匹配方法的 JavaDoc
                for (MethodInfo method : interfaceInfo.getMethods()) {
                    MethodJavaDoc methodDoc = javadoc.findMethod(method.getMethodName());
                    if (methodDoc != null) {
                        method.setDescription(methodDoc.getDescription());
                        method.setReturnDescription(methodDoc.getReturnDescription());
                        
                        // 匹配参数的 JavaDoc
                        if (method.getParameters() != null) {
                            for (int i = 0; i < method.getParameters().size(); i++) {
                                ParameterInfo param = method.getParameters().get(i);
                                
                                // 尝试通过参数名匹配
                                String paramDoc = methodDoc.getParameterDescription(param.name);
                                if (paramDoc == null && i < methodDoc.getParameterNames().size()) {
                                    // 如果名称不匹配，尝试通过位置匹配
                                    String docParamName = methodDoc.getParameterNames().get(i);
                                    param.name = docParamName; // 更新参数名
                                    paramDoc = methodDoc.getParameterDescription(docParamName);
                                }
                                
                                if (paramDoc != null) {
                                    param.setDescription(paramDoc);
                                }
                            }
                        }
                    }
                }
                
                enrichedCount++;
            }
        }
        
        progress.addLog(String.format("✅ 成功为 %d/%d 个接口补充了 JavaDoc", 
                enrichedCount, interfaces.size()));
        progressCallback.accept(progress);
        
        return interfaces;
    }
    
    /**
     * 解析 Git 仓库中的 Java 源码，返回用于元数据的 Map 结构
     *
     * @param repoDirectory Git 仓库目录
     * @param progress 进度对象 (可为 null)
     * @param progressCallback 进度回调 (可为 null)
     */
    public void extractJavadocMaps(
            File repoDirectory,
            Map<String, String> interfaceDocs,
            Map<String, String> methodDocs,
            Map<String, List<String>> paramNames,
            Map<String, String> methodParamDocs,
            Map<String, String> methodReturnDocs,
            PomParseProgress progress,
            Consumer<PomParseProgress> progressCallback
    ) throws IOException {
        
        if (progress != null && progressCallback != null) {
            progress.addLog("开始解析 JavaDoc...");
            progressCallback.accept(progress);
        }

        // 查找所有 Java 源文件
        List<Path> javaFiles = findJavaFiles(repoDirectory.toPath());
        
        if (progress != null && progressCallback != null) {
            progress.addLog(String.format("找到 %d 个 Java 文件", javaFiles.size()));
            progressCallback.accept(progress);
        }

        for (Path javaFile : javaFiles) {
            try {
                InterfaceJavaDoc doc = parseJavaFile(javaFile.toFile());
                if (doc != null && doc.isInterface()) {
                    String interfaceName = doc.getFullyQualifiedName();
                    
                    // 1. 接口文档
                    if (doc.getDescription() != null && !doc.getDescription().isEmpty()) {
                        interfaceDocs.put(interfaceName, doc.getDescription());
                    }
                    
                    // 2. 方法文档
                    for (MethodJavaDoc method : doc.getMethods()) {
                        String methodKey = interfaceName + "#" + method.getMethodName();
                        
                        if (method.getDescription() != null && !method.getDescription().isEmpty()) {
                            methodDocs.put(methodKey, method.getDescription());
                        }
                        
                        // 3. 返回值文档
                        if (method.getReturnDescription() != null && !method.getReturnDescription().isEmpty()) {
                            methodReturnDocs.put(methodKey, method.getReturnDescription());
                        }
                        
                        // 4. 参数名列表
                        if (method.getParameterNames() != null && !method.getParameterNames().isEmpty()) {
                            paramNames.put(methodKey, new ArrayList<>(method.getParameterNames()));
                        }
                        
                        // 5. 参数文档
                        for (Map.Entry<String, String> entry : method.getParameterDescriptions().entrySet()) {
                            String paramKey = methodKey + "#" + entry.getKey();
                            methodParamDocs.put(paramKey, entry.getValue());
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("解析 Java 文件失败: {} - {}", javaFile, e.getMessage());
            }
        }
        
        if (progress != null && progressCallback != null) {
            progress.addLog(String.format("成功解析 %d 个接口的 JavaDoc", interfaceDocs.size()));
            progressCallback.accept(progress);
        }
    }
    
    /**
     * 查找目录下的所有 Java 文件
     */
    private List<Path> findJavaFiles(Path directory) throws IOException {
        return Files.walk(directory)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.toString().contains("/test/"))  // 排除测试代码
                .collect(Collectors.toList());
    }
    
    /**
     * 解析单个 Java 文件
     */
    private InterfaceJavaDoc parseJavaFile(File javaFile) throws Exception {
        CompilationUnit cu = javaParser.parse(javaFile).getResult()
                .orElseThrow(() -> new Exception("解析失败"));
        
        // 查找接口声明
        Optional<ClassOrInterfaceDeclaration> interfaceDecl = cu.findFirst(
                ClassOrInterfaceDeclaration.class,
                decl -> decl.isInterface()
        );
        
        if (!interfaceDecl.isPresent()) {
            return null;
        }
        
        ClassOrInterfaceDeclaration iface = interfaceDecl.get();
        InterfaceJavaDoc doc = new InterfaceJavaDoc();
        
        // 获取完全限定名
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElse("");
        doc.setFullyQualifiedName(packageName + "." + iface.getNameAsString());
        doc.setInterface(true);
        
        // 提取接口 JavaDoc
        iface.getJavadocComment().ifPresent(comment -> {
            Javadoc javadoc = comment.parse();
            doc.setDescription(javadoc.getDescription().toText().trim());
        });
        
        // 提取方法 JavaDoc
        for (MethodDeclaration method : iface.getMethods()) {
            MethodJavaDoc methodDoc = new MethodJavaDoc();
            methodDoc.setMethodName(method.getNameAsString());
            
            method.getJavadocComment().ifPresent(comment -> {
                Javadoc javadoc = comment.parse();
                methodDoc.setDescription(javadoc.getDescription().toText().trim());
                
                // 提取 @param 标签
                for (JavadocBlockTag tag : javadoc.getBlockTags()) {
                    if (tag.getTagName().equals("param")) {
                        String paramName = tag.getName().orElse("");
                        String paramDesc = tag.getContent().toText().trim();
                        methodDoc.addParameter(paramName, paramDesc);
                    } else if (tag.getTagName().equals("return")) {
                        methodDoc.setReturnDescription(tag.getContent().toText().trim());
                    }
                }
            });
            
            // 记录参数名称（从方法签名）
            for (Parameter param : method.getParameters()) {
                if (!methodDoc.getParameterNames().contains(param.getNameAsString())) {
                    methodDoc.getParameterNames().add(param.getNameAsString());
                }
            }
            
            doc.addMethod(methodDoc);
        }
        
        return doc;
    }
    
    /**
     * 接口 JavaDoc 信息
     */
    @Data
    private static class InterfaceJavaDoc {
        private String fullyQualifiedName;
        private boolean isInterface;
        private String description;
        private List<MethodJavaDoc> methods = new ArrayList<>();
        
        public void addMethod(MethodJavaDoc method) {
            methods.add(method);
        }
        
        public MethodJavaDoc findMethod(String methodName) {
            return methods.stream()
                    .filter(m -> m.getMethodName().equals(methodName))
                    .findFirst()
                    .orElse(null);
        }
    }
    
    /**
     * 方法 JavaDoc 信息
     */
    @Data
    private static class MethodJavaDoc {
        private String methodName;
        private String description;
        private String returnDescription;
        private Map<String, String> parameterDescriptions = new LinkedHashMap<>();
        private List<String> parameterNames = new ArrayList<>();
        
        public void addParameter(String name, String description) {
            parameterDescriptions.put(name, description);
            if (!parameterNames.contains(name)) {
                parameterNames.add(name);
            }
        }
        
        public String getParameterDescription(String paramName) {
            return parameterDescriptions.get(paramName);
        }
    }
}
