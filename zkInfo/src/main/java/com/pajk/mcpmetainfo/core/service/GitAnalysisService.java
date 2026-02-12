package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.wizard.GitProjectMetadata;
import com.pajk.mcpmetainfo.core.model.wizard.GitRepositoryConfig;
import com.pajk.mcpmetainfo.core.model.wizard.PomParseProgress;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Git 项目分析服务
 * 负责克隆代码仓库并提取元数据
 */
@Slf4j
@Service
public class GitAnalysisService {

    @Autowired
    private JavaDocParserService javaDocParserService;
    
    @Autowired
    private GitRepositoryService gitRepositoryService;

    /**
     * 克隆并分析 Git 项目
     */
    public GitProjectMetadata analyzeGitProject(String gitUrl, String branch, String token) {
        GitProjectMetadata metadata = new GitProjectMetadata();
        metadata.setSuccess(false);

        // 创建临时目录
        File projectDir = null;
        try {
            // 1. 克隆代码 (使用 GitRepositoryService)
            log.info("开始克隆 Git 仓库: {} (分支: {})", gitUrl, branch);
            
            GitRepositoryConfig config = GitRepositoryConfig.builder()
                    .repositoryUrl(gitUrl)
                    .branch(branch)
                    .accessToken(token)
                    .build();
            
            // 创建简单的进度回调
            PomParseProgress progress = new PomParseProgress();
            java.util.function.Consumer<PomParseProgress> progressCallback = p -> log.debug("Git Clone: {}", p.getLastLog());
            
            projectDir = gitRepositoryService.cloneRepository(config, progress, progressCallback);
            
            // 2. 提取 Git 信息 (Owner) - 这个目前在这个Service内部实现，或者移到 GitRepositoryService
            try (Git git = Git.open(projectDir)) {
                 extractGitInfo(git, metadata);
            } catch (Exception e) {
                log.warn("无法打开 Git 仓库读取提交信息: {}", e.getMessage());
            }
            
            // 3. 解析配置文件 (Application Name, Port)
            extractconfigInfo(projectDir, metadata);
            
            // 4. 解析 POM 文件 (Description, GAV)
            extractPomInfo(projectDir, metadata);
            
            // 5. 解析 Javadoc
            log.info("开始解析 Javadoc...");
            Map<String, String> interfaceDocs = new HashMap<>();
            Map<String, String> methodDocs = new HashMap<>();
            Map<String, List<String>> paramNames = new HashMap<>();
            Map<String, String> methodParamDocs = new HashMap<>();
            Map<String, String> methodReturnDocs = new HashMap<>();
            
            // 使用 JavaDocParserService，传入 null 进度回调（同步调用）
            javaDocParserService.extractJavadocMaps(
                projectDir, 
                interfaceDocs, 
                methodDocs, 
                paramNames, 
                methodParamDocs, 
                methodReturnDocs,
                null, 
                null
            );
            
            metadata.setInterfaceJavadocMap(interfaceDocs);
            metadata.setMethodJavadocMap(methodDocs);
            metadata.setMethodParameterNamesMap(paramNames);
            metadata.setMethodParamJavadocMap(methodParamDocs);
            metadata.setMethodReturnJavadocMap(methodReturnDocs);
            
            metadata.setSuccess(true);
            log.info("Git 项目分析完成: {}", metadata.getApplicationName());
            
        } catch (Exception e) {
            log.error("Git 项目分析失败", e);
            metadata.setErrorMessage("分析失败: " + e.getMessage());
        } finally {
            // 清理临时文件
            if (projectDir != null) {
                gitRepositoryService.cleanupTempDirectory(projectDir);
            }
        }
        
        return metadata;
    }

    private void extractGitInfo(Git git, GitProjectMetadata metadata) {
        try {
            Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
            for (RevCommit commit : commits) {
                PersonIdent author = commit.getAuthorIdent();
                metadata.setOwner(author.getName()); // 使用最后一次提交的作者作为 owner
                log.info("识别到 Owner: {}", author.getName());
            }
        } catch (Exception e) {
            log.warn("提取 Git 提交信息失败", e);
        }
    }

    private void extractconfigInfo(File projectDir, GitProjectMetadata metadata) {
        // 查找 application.yml / application.properties
        try (Stream<Path> paths = Files.walk(projectDir.toPath())) {
            paths.filter(p -> {
                String name = p.getFileName().toString();
                return name.equals("application.yml") || name.equals("application.yaml") || 
                       name.equals("application.properties") || name.equals("dubbo-provider.xml");
            }).forEach(path -> {
                File file = path.toFile();
                String fileName = file.getName();
                
                if (fileName.endsWith(".xml")) {
                    parseXmlConfig(file, metadata);
                } else if (fileName.endsWith(".properties")) {
                    parsePropertiesConfig(file, metadata);
                } else {
                    parseYamlConfig(file, metadata);
                }
            });
        } catch (IOException e) {
            log.warn("遍历文件失败", e);
        }
    }

    private void parseYamlConfig(File file, GitProjectMetadata metadata) {
        try (FileInputStream fis = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            Map<String, Object> obj = yaml.load(fis);
            Properties props = flattenedYaml(obj, null);
            updateMetadataFromProps(props, metadata);
        } catch (Exception e) {
            log.warn("解析 YAML 失败: {}", file.getName());
        }
    }
    
    @SuppressWarnings("unchecked")
    private Properties flattenedYaml(Map<String, Object> map, String prefix) {
        Properties props = new Properties();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix == null ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                props.putAll(flattenedYaml((Map<String, Object>) value, key));
            } else if (value != null) {
                props.put(key, value.toString());
            }
        }
        return props;
    }

    private void parsePropertiesConfig(File file, GitProjectMetadata metadata) {
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            updateMetadataFromProps(props, metadata);
        } catch (Exception e) {
            log.warn("解析 Properties 失败: {}", file.getName());
        }
    }

    private void updateMetadataFromProps(Properties props, GitProjectMetadata metadata) {
        if (metadata.getApplicationName() == null) {
            String appName = props.getProperty("spring.application.name");
            if (appName == null) appName = props.getProperty("dubbo.application.name");
            if (appName != null) metadata.setApplicationName(appName);
        }
        
        if (metadata.getServerPort() == null) {
            String port = props.getProperty("server.port");
            if (port != null) {
                try {
                    metadata.setServerPort(Integer.parseInt(port));
                } catch (NumberFormatException ignored) {}
            }
        }
        
        if (metadata.getDubboPort() == null) {
            String port = props.getProperty("dubbo.protocol.port");
            if (port != null) {
                try {
                    metadata.setDubboPort(Integer.parseInt(port));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void parseXmlConfig(File file, GitProjectMetadata metadata) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            
            if (metadata.getApplicationName() == null) {
                NodeList apps = doc.getElementsByTagName("dubbo:application");
                if (apps.getLength() > 0) {
                    Element app = (Element) apps.item(0);
                    if (app.hasAttribute("name")) {
                        metadata.setApplicationName(app.getAttribute("name"));
                    }
                }
            }
            
            if (metadata.getDubboPort() == null) {
                NodeList protocols = doc.getElementsByTagName("dubbo:protocol");
                for (int i = 0; i < protocols.getLength(); i++) {
                    Element p = (Element) protocols.item(i);
                    if ("dubbo".equals(p.getAttribute("name")) && p.hasAttribute("port")) {
                        try {
                            metadata.setDubboPort(Integer.parseInt(p.getAttribute("port")));
                        } catch (Exception ignored) {}
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("解析 XML 失败", e);
        }
    }

    private void extractPomInfo(File projectDir, GitProjectMetadata metadata) {
        File pomFile = new File(projectDir, "pom.xml");
        if (!pomFile.exists()) return;
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);
            
            // 提取 GAV
            NodeList projectNodes = doc.getChildNodes();
            for (int i = 0; i < projectNodes.getLength(); i++) {
                if (projectNodes.item(i) instanceof Element) {
                    Element project = (Element) projectNodes.item(i);
                    if ("project".equals(project.getNodeName())) {
                        NodeList children = project.getChildNodes();
                        for (int j = 0; j < children.getLength(); j++) {
                            if (children.item(j) instanceof Element) {
                                Element child = (Element) children.item(j);
                                switch (child.getNodeName()) {
                                    case "groupId": metadata.setGroupId(child.getTextContent().trim()); break;
                                    case "artifactId": metadata.setArtifactId(child.getTextContent().trim()); break;
                                    case "version": metadata.setVersion(child.getTextContent().trim()); break;
                                    case "description": 
                                        if (metadata.getDescription() == null) {
                                            metadata.setDescription(child.getTextContent().trim());
                                        }
                                        break;
                                    case "name": metadata.setProjectName(child.getTextContent().trim()); break;
                                }
                            }
                        }
                    }
                }
            }
            
            // 如果 groupId 没找到，尝试找 parent
            if (metadata.getGroupId() == null) {
                NodeList parentNodes = doc.getElementsByTagName("parent");
                if (parentNodes.getLength() > 0) {
                    Element parent = (Element) parentNodes.item(0);
                    NodeList parentChildren = parent.getChildNodes();
                    for (int k = 0; k < parentChildren.getLength(); k++) {
                        if (parentChildren.item(k) instanceof Element) {
                            Element pk = (Element) parentChildren.item(k);
                            if ("groupId".equals(pk.getNodeName())) {
                                metadata.setGroupId(pk.getTextContent().trim());
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("解析 POM 失败", e);
        }

    }
}
