package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.config.MavenRepositoryConfig;
import com.pajk.mcpmetainfo.core.model.wizard.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * POM 依赖分析服务
 * 负责解析 POM 文件、下载 JAR 包、提取 Dubbo 接口信息
 */
@Slf4j
@Service
public class PomDependencyAnalyzerService {
    
    @Autowired
    private JarScannerService jarScannerService;
    
    @Autowired
    private MavenRepositoryConfig mavenConfig;
    
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/zkinfo-wizard";
    private static final String MAVEN_SETTINGS_PATH = System.getProperty("user.home") + "/.m2/settings.xml";
    
    /**
     * 解析 POM 并提取接口信息（带进度回调）
     * 
     * @param pomContent POM XML 内容
     * @param progressCallback 进度回调函数
     * @return 解析结果
     */
    public PomParseResult parsePomAndExtractInterfaces(String pomContent, Consumer<PomParseProgress> progressCallback) {
        PomParseProgress progress = PomParseProgress.builder()
                .currentStage(PomParseProgress.Stage.PARSING_POM)
                .stageDescription("开始解析 POM 依赖")
                .progressPercentage(0)
                .completed(false)
                .hasError(false)
                .build();
        
        try {
            // 阶段 1: 解析 POM 依赖
            progress.addLog("开始解析 POM 依赖...");
            progressCallback.accept(progress);
            
            PomParseResult.PomParseResultBuilder resultBuilder = PomParseResult.builder();
            
            List<MavenDependency> dependencies = parseDependenciesAndSetMeta(pomContent, progress, resultBuilder, progressCallback);
            
            if (dependencies.isEmpty()) {
                progress.addLog("⚠️ 未找到任何依赖");
                progress.setHasError(true);
                progress.setErrorMessage("POM 中未找到任何依赖");
                progressCallback.accept(progress);
                
                return resultBuilder
                        .success(false)
                        .errorMessage("POM 中未找到任何依赖")
                        .build();
            }
            
            progress.setTotalDependencies(dependencies.size());
            progress.setParsedDependencies(dependencies.size());
            progress.setProgressPercentage(20);
            progress.addLog(String.format("✅ 成功解析 %d 个依赖", dependencies.size()));
            progressCallback.accept(progress);
            
            // 阶段 2: 下载 JAR 包
            progress.setCurrentStage(PomParseProgress.Stage.DOWNLOADING_JARS);
            progress.setStageDescription("正在下载 JAR 包");
            progress.setProgressPercentage(30);
            progress.addLog("开始下载 JAR 包...");
            progressCallback.accept(progress);
            
            AtomicInteger downloadedCount = new AtomicInteger(0);
            for (int i = 0; i < dependencies.size(); i++) {
                MavenDependency dep = dependencies.get(i);
                try {
                    downloadJar(dep, progress, progressCallback);
                    downloadedCount.incrementAndGet();
                    
                    int percentage = 30 + (i + 1) * 30 / dependencies.size();
                    progress.setProgressPercentage(percentage);
                    progress.setDownloadedJars(downloadedCount.get());
                    progressCallback.accept(progress);
                } catch (Exception e) {
                    log.warn("下载 JAR 失败: {}, 错误: {}", dep.getCoordinate(), e.getMessage());
                    dep.setDownloadStatus(MavenDependency.DownloadStatus.FAILED);
                    dep.setErrorMessage(e.getMessage());
                    progress.addLog(String.format("❌ 下载失败: %s - %s", dep.getCoordinate(), e.getMessage()));
                }
            }
            
            progress.addLog(String.format("✅ 成功下载 %d/%d 个 JAR 包", downloadedCount.get(), dependencies.size()));
            progressCallback.accept(progress);
            
            // 阶段 3: 提取接口信息
            progress.setCurrentStage(PomParseProgress.Stage.EXTRACTING_INTERFACES);
            progress.setStageDescription("正在提取接口信息");
            progress.setProgressPercentage(70);
            progress.addLog("开始提取 Dubbo 接口...");
            progressCallback.accept(progress);
            
            List<DubboInterfaceInfo> allInterfaces = new ArrayList<>();
            for (MavenDependency dep : dependencies) {
                if (dep.getDownloadStatus() == MavenDependency.DownloadStatus.SUCCESS 
                    && dep.getLocalPath() != null) {
                    
                    // 如果是 POM 包，跳过接口提取
                    if ("pom".equalsIgnoreCase(dep.getType())) {
                        progress.addLog(String.format("ℹ️ 跳过 POM 包接口提取: %s", dep.getCoordinate()));
                        continue;
                    }

                    try {
                        List<DubboInterfaceInfo> interfaces = extractDubboInterfaces(
                            new File(dep.getLocalPath()), 
                            dep.getCoordinate(),
                            progress,
                            progressCallback
                        );
                        allInterfaces.addAll(interfaces);
                    } catch (Exception e) {
                        log.warn("分析 JAR 接口失败: {}, 错误: {}", dep.getCoordinate(), e.getMessage());
                        progress.addLog(String.format("⚠️ 分析接口失败: %s - %s", dep.getCoordinate(), e.getMessage()));
                    }
                }
            }
            
            progress.setProgressPercentage(90);
            progress.setExtractedInterfaces(allInterfaces.size());
            progress.addLog(String.format("✅ 成功提取 %d 个接口", allInterfaces.size()));
            progressCallback.accept(progress);
            
            // 完成
            progress.setCompleted(true);
            progress.setProgressPercentage(100);
            progress.setStageDescription("解析完成");
            progress.addLog("🎉 POM 解析和接口提取完全成功！");
            
            int methodCount = 0;
            for (DubboInterfaceInfo iface : allInterfaces) {
                if (iface.getMethods() != null) {
                    methodCount += iface.getMethods().size();
                }
            }
            
            PomParseResult result = resultBuilder
                    .success(true)
                    .dependencies(dependencies)
                    .interfaces(allInterfaces)
                    .jarCount(downloadedCount.get())
                    .interfaceCount(allInterfaces.size())
                    .methodCount(methodCount)
                    .build();
            
            progress.setResult(result);
            progressCallback.accept(progress);
            
            return result;
            
        } catch (Exception e) {
            log.error("POM 解析失败", e);
            progress.setHasError(true);
            progress.setErrorMessage("解析失败: " + e.getMessage());
            progress.addLog("❌ 解析失败: " + e.getMessage());
            progressCallback.accept(progress);
            
            return PomParseResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
    
    /**
     * 解析 POM 中的依赖和主项目坐标
     */
    private List<MavenDependency> parseDependenciesAndSetMeta(String pomContent, PomParseProgress progress, 
                                                            PomParseResult.PomParseResultBuilder resultBuilder,
                                                            Consumer<PomParseProgress> progressCallback) throws Exception {
        List<MavenDependency> dependencies = new ArrayList<>();
        
        // 如果不是完整的 POM，添加外层标签
        String xmlContent = pomContent.trim();
        if (!xmlContent.startsWith("<?xml") && !xmlContent.startsWith("<project")) {
            xmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project>" + xmlContent + "</project>";
        }
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(xmlContent.getBytes("UTF-8")));
        
        // 1. 尝试提取主项目坐标 (GAV)
        NodeList projectNodes = document.getChildNodes();
        for (int i = 0; i < projectNodes.getLength(); i++) {
            if (projectNodes.item(i) instanceof Element) {
                Element project = (Element) projectNodes.item(i);
                if ("project".equals(project.getNodeName())) {
                    NodeList children = project.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        if (children.item(j) instanceof Element) {
                            Element child = (Element) children.item(j);
                            if ("groupId".equals(child.getNodeName())) resultBuilder.groupId(child.getTextContent().trim());
                            else if ("artifactId".equals(child.getNodeName())) resultBuilder.artifactId(child.getTextContent().trim());
                            else if ("version".equals(child.getNodeName())) resultBuilder.version(child.getTextContent().trim());
                        }
                    }
                }
            }
        }
        
        // 2. 如果主项目没有 GAV，使用第一个依赖的 GAV 作为提示（可选）

        if (resultBuilder.build().getGroupId() == null || resultBuilder.build().getArtifactId() == null) {
            NodeList deps = document.getElementsByTagName("dependency");
            if (deps.getLength() > 0) {
                Element firstDep = (Element) deps.item(0);
                NodeList children = firstDep.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element) {
                        Element child = (Element) children.item(i);
                        if ("groupId".equals(child.getNodeName()) && resultBuilder.build().getGroupId() == null) 
                            resultBuilder.groupId(child.getTextContent().trim());
                        else if ("artifactId".equals(child.getNodeName()) && resultBuilder.build().getArtifactId() == null) 
                            resultBuilder.artifactId(child.getTextContent().trim());
                        else if ("version".equals(child.getNodeName()) && resultBuilder.build().getVersion() == null) 
                            resultBuilder.version(child.getTextContent().trim());
                    }
                }
            }
        }


        // 2. 提取依赖
        NodeList dependencyNodes = document.getElementsByTagName("dependency");
        
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element depElement = (Element) dependencyNodes.item(i);
            
            String groupId = getTextContent(depElement, "groupId");
            String artifactId = getTextContent(depElement, "artifactId");
            String version = getTextContent(depElement, "version");
            String type = getTextContent(depElement, "type", "jar");
            String scope = getTextContent(depElement, "scope", "compile");
            
            if (groupId != null && artifactId != null && version != null) {
                MavenDependency dep = MavenDependency.builder()
                        .groupId(groupId)
                        .artifactId(artifactId)
                        .version(version)
                        .type(type)
                        .scope(scope)
                        .downloadStatus(MavenDependency.DownloadStatus.PENDING)
                        .build();
                
                dependencies.add(dep);
                progress.addLog(String.format("发现依赖: %s", dep.getCoordinate()));
                progressCallback.accept(progress);
            }
        }
        
        return dependencies;
    }

    
    /**
     * 获取 Maven 私有仓库 URL（企业级）
     * 优先级:
     * 1. application.yml 中配置的 Nexus（生产环境）
     * 2. ~/.m2/settings.xml 中的镜像（本地开发）
     * 3. 没有配置时抛出异常
     */
    private String getMavenRepositoryUrl() {
        // 优先使用配置的 Nexus
        if (mavenConfig.hasNexusConfig()) {
            return mavenConfig.getNexus().getUrl();
        }
        
        // 如果配置了使用 settings.xml，则尝试读取
        if (mavenConfig.isUseSettingsXml()) {
            String urlFromSettings = readRepositoryFromSettingsXml();
            if (urlFromSettings != null) {
                log.info("✅ 从 ~/.m2/settings.xml 读取仓库: {}", urlFromSettings);
                return urlFromSettings;
            }
        }
        
        // 都没有配置，抛出异常
        throw new RuntimeException("未配置 Maven 私有仓库！请在 application.yml 中配置 maven.nexus 或确保 ~/.m2/settings.xml 存在");
    }
    
    /**
     * 从 settings.xml 读取仓库 URL
     */
    private String readRepositoryFromSettingsXml() {
        try {
            File settingsFile = new File(MAVEN_SETTINGS_PATH);
            if (!settingsFile.exists()) {
                log.warn("Maven settings.xml 不存在: {}", MAVEN_SETTINGS_PATH);
                return null;
            }
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(settingsFile);
            
            NodeList mirrors = doc.getElementsByTagName("mirror");
            String firstMirrorUrl = null;
            
            for (int i = 0; i < mirrors.getLength(); i++) {
                Element mirror = (Element) mirrors.item(i);
                NodeList urlNodes = mirror.getElementsByTagName("url");
                if (urlNodes.getLength() > 0) {
                    String url = urlNodes.item(0).getTextContent().trim();
                    
                    // 保存第一个镜像作为备选
                    if (firstMirrorUrl == null) {
                        firstMirrorUrl = url;
                    }
                    
                    // 优先策略：如果 URL 包含 localhost, 127.0.0.1 或 192.168, 10. (私有网段)，直接返回
                    // 这里特别针对用户的 localhost:8881 需求
                    if (url.contains("localhost") || url.contains("127.0.0.1") || 
                        url.contains("192.168.") || url.contains("10.")) {
                        log.info("🎯 命中私有/本地仓库镜像: {}", url);
                        return url;
                    }
                }
            }
            
            // 如果没有找到特定的私有镜像，返回第一个
            if (firstMirrorUrl != null) {
                log.info("使用 settings.xml 中的第一个镜像: {}", firstMirrorUrl);
                return firstMirrorUrl;
            }
            
            return null;
        } catch (Exception e) {
            log.warn("读取 settings.xml 失败: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 下载 JAR 包（支持认证）
     */
    private void downloadJar(MavenDependency dependency, PomParseProgress progress, Consumer<PomParseProgress> progressCallback) throws Exception {
        String repositoryUrl = getMavenRepositoryUrl();
        String groupPath = dependency.getGroupId().replace('.', '/');
        
        // 使用 type 作为文件扩展名，默认为 jar
        String type = dependency.getType();
        if (type == null || type.isEmpty()) {
            type = "jar";
        }
        
        String jarUrl = String.format("%s/%s/%s/%s/%s-%s.%s",
                repositoryUrl,
                groupPath,
                dependency.getArtifactId(),
                dependency.getVersion(),
                dependency.getArtifactId(),
                dependency.getVersion(),
                type);
        
        dependency.setDownloadStatus(MavenDependency.DownloadStatus.DOWNLOADING);
        progress.addLog(String.format("正在下载: %s (type=%s)", dependency.getCoordinate(), type));
        progress.addLog(String.format("  仓库: %s", repositoryUrl));
        progressCallback.accept(progress);
        
        // 创建临时目录
        Path tempDir = Paths.get(TEMP_DIR);
        Files.createDirectories(tempDir);
        
        // 下载文件，文件名包含类型后缀
        String fileName = String.format("%s-%s-%s.%s",
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getVersion(),
                type);
        Path targetPath = tempDir.resolve(fileName);
        
        try {
            URL url = new URL(jarUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // 如果配置了认证信息，添加 Basic Auth
            if (mavenConfig.hasNexusConfig() && 
                mavenConfig.getNexus().getUsername() != null && 
                !mavenConfig.getNexus().getUsername().isEmpty()) {
                
                String auth = mavenConfig.getNexus().getUsername() + ":" + 
                             (mavenConfig.getNexus().getPassword() != null ? mavenConfig.getNexus().getPassword() : "");
                String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());
                connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                log.debug("使用认证下载 JAR: {}", mavenConfig.getNexus().getUsername());
            }
            
            connection.setConnectTimeout(mavenConfig.getNexus().getConnectTimeout());
            connection.setReadTimeout(mavenConfig.getNexus().getReadTimeout());
            connection.connect();
            
            // 检查响应码
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException(String.format("下载失败: HTTP %d", responseCode));
            }
            
            Files.copy(connection.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            dependency.setDownloadStatus(MavenDependency.DownloadStatus.SUCCESS);
            dependency.setLocalPath(targetPath.toAbsolutePath().toString());
            progress.addLog(String.format("✅ 下载成功: %s (%d bytes)", 
                dependency.getCoordinate(), 
                Files.size(targetPath)));
            
        } catch (Exception e) {
            dependency.setDownloadStatus(MavenDependency.DownloadStatus.FAILED);
            dependency.setErrorMessage(e.getMessage());
            progress.addLog(String.format("❌ 下载失败: %s", dependency.getCoordinate()));
            progress.addLog(String.format("  URL: %s", jarUrl));
            progress.addLog(String.format("  错误: %s", e.getMessage()));
            throw e;
        }
    }
    
    /**
     * 从 JAR 包中提取 Dubbo 接口
     */
    private List<DubboInterfaceInfo> extractDubboInterfaces(File jarFile, String jarName, PomParseProgress progress, Consumer<PomParseProgress> progressCallback) throws Exception {
        // 使用 JarScannerService 进行扫描
        return jarScannerService.scanJarForDubboInterfaces(jarFile, jarName, progress, progressCallback);
    }
    
    /**
     * 从接口 Class 中提取方法信息
     */
    private List<MethodInfo> extractMethods(Class<?> interfaceClass) {
        List<MethodInfo> methods = new ArrayList<>();
        
        for (Method method : interfaceClass.getDeclaredMethods()) {
            List<ParameterInfo> parameters = new ArrayList<>();
            
            for (Parameter parameter : method.getParameters()) {
                parameters.add(ParameterInfo.builder()
                        .name(parameter.getName())
                        .type(parameter.getType().getName())
                        .typeSimpleName(parameter.getType().getSimpleName())
                        .build());
            }
            
            methods.add(MethodInfo.builder()
                    .methodName(method.getName())
                    .returnType(method.getReturnType().getName())
                    .returnTypeSimpleName(method.getReturnType().getSimpleName())
                    .parameters(parameters)
                    .build());
        }
        
        return methods;
    }
    
    /**
     * 获取 XML 元素的文本内容
     */
    private String getTextContent(Element parent, String tagName) {
        return getTextContent(parent, tagName, null);
    }
    
    private String getTextContent(Element parent, String tagName, String defaultValue) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            String content = nodes.item(0).getTextContent();
            return content != null && !content.trim().isEmpty() ? content.trim() : defaultValue;
        }
        return defaultValue;
    }
}
