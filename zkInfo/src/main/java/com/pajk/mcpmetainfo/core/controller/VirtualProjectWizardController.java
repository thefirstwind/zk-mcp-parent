package com.pajk.mcpmetainfo.core.controller;

import com.pajk.mcpmetainfo.core.model.wizard.PomParseProgress;
import com.pajk.mcpmetainfo.core.model.wizard.PomParseResult;
import com.pajk.mcpmetainfo.core.model.wizard.GitProjectMetadata;
import com.pajk.mcpmetainfo.core.model.wizard.EnrichedMetadata;
import com.pajk.mcpmetainfo.core.model.wizard.EnrichmentRequest;
import com.pajk.mcpmetainfo.core.service.AiMetadataEnrichmentService;
import com.pajk.mcpmetainfo.core.service.GitAnalysisService;
import com.pajk.mcpmetainfo.core.service.PomDependencyAnalyzerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 虚拟项目向导 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/wizard")
public class VirtualProjectWizardController {
    
    @Autowired
    private PomDependencyAnalyzerService pomAnalyzerService;

    @Autowired
    private GitAnalysisService gitAnalysisService;

    @Autowired
    private AiMetadataEnrichmentService aiEnrichmentService;

    @Autowired
    private com.pajk.mcpmetainfo.core.service.ZooKeeperService zkService;

    @Autowired
    private com.pajk.mcpmetainfo.core.service.NacosMcpRegistrationService nacosRegistrationService;

    @Autowired
    private com.pajk.mcpmetainfo.core.util.McpToolSchemaGenerator mcpToolSchemaGenerator;

    
    // 存储 SSE 连接
    private final Map<String, SseEmitter> progressEmitters = new ConcurrentHashMap<>();
    
    /**
     * 建立 SSE 连接以接收解析进度
     */
    @GetMapping(value = "/parse-pom/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getParseProgress(@RequestParam String sessionId) {
        log.info("建立 SSE 连接: sessionId={}", sessionId);
        
        SseEmitter emitter = new SseEmitter(300000L); // 5 分钟超时
        progressEmitters.put(sessionId, emitter);
        
        // 发送初始连接成功事件
        try {
            emitter.send(SseEmitter.event().name("connected").data("SSE Established"));
        } catch (Exception e) {
            log.warn("Failed to send initial SSE event: {}", e.getMessage());
        }
        
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成: sessionId={}", sessionId);
            progressEmitters.remove(sessionId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时: sessionId={}", sessionId);
            progressEmitters.remove(sessionId);
        });
        
        emitter.onError(e -> {
            log.error("SSE 连接错误: sessionId={}", sessionId, e);
            progressEmitters.remove(sessionId);
        });
        
        return emitter;
    }
    
    /**
     * 解析 POM 依赖（异步）
     */
    @PostMapping("/parse-pom")
    public ResponseEntity<?> parsePom(@RequestBody Map<String, String> request) {
        String projectName = request.get("projectName");
        String pomContent = request.get("pomContent");
        String sessionId = request.get("sessionId");
        
        log.info("收到 POM 解析请求: projectName={}, sessionId={}", projectName, sessionId);
        
        if (pomContent == null || pomContent.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "POM 内容不能为空"));
        }
        
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = String.valueOf(System.currentTimeMillis());
        }
        
        final String finalSessionId = sessionId;
        
        // 异步执行解析
        CompletableFuture.runAsync(() -> {
            try {
                pomAnalyzerService.parsePomAndExtractInterfaces(pomContent, progress -> {
                    sendProgress(finalSessionId, progress);
                });
            } catch (Exception e) {
                log.error("POM 解析失败: sessionId={}", finalSessionId, e);
                PomParseProgress errorProgress = PomParseProgress.builder()
                        .hasError(true)
                        .errorMessage("解析失败: " + e.getMessage())
                        .completed(true)
                        .build();
                sendProgress(finalSessionId, errorProgress);
            }
        });
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sessionId", finalSessionId,
                "message", "解析任务已启动，请通过 SSE 获取进度"
        ));
    }
    
    /**
     * 发送进度更新
     */
    private void sendProgress(String sessionId, PomParseProgress progress) {
        SseEmitter emitter = progressEmitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(progress));
                
                // 如果完成，关闭连接
                if (progress.isCompleted() || progress.isHasError()) {
                    emitter.complete();
                    progressEmitters.remove(sessionId);
                }
            } catch (Exception e) {
                log.error("发送 SSE 进度失败: sessionId={}", sessionId, e);
                progressEmitters.remove(sessionId);
            }
        }
    }
    /**
     * Git 项目元数据提取接口
     */
    @PostMapping("/git-meta")
    public GitProjectMetadata extractGitMetadata(@RequestBody GitMetaRequest request) {
        log.info("收到 Git 元数据提取请求: {}", request.getGitUrl());
        
        try {
            return gitAnalysisService.analyzeGitProject(
                request.getGitUrl(),
                request.getBranch(),
                request.getToken()
            );
        } catch (Exception e) {
            log.error("Git 分析异常", e);
            GitProjectMetadata errorMeta = new GitProjectMetadata();
            errorMeta.setSuccess(false);
            errorMeta.setErrorMessage(e.getMessage());
            return errorMeta;
        }
    }

    @PostMapping("/enrich-metadata")
    public EnrichedMetadata enrichMetadata(@RequestBody EnrichmentRequest request) {
        log.info("收到元数据补全请求: {}", request.getProjectName());
        try {
            return aiEnrichmentService.enrichMetadata(request);
        } catch (Exception e) {
            log.error("元数据补全失败", e);
            throw new RuntimeException("补全失败: " + e.getMessage());
        }
    }

    /**
     * 检查项目名称是否存在
     */
    @PostMapping("/check-name")
    public ResponseEntity<?> checkProjectName(@RequestBody Map<String, String> request) {
        String projectName = request.get("projectName");
        if (projectName == null || projectName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "项目名称不能为空"));
        }
        
        String serviceName = "virtual-" + projectName.trim().toLowerCase().replace(" ", "-");
        // Check against Nacos
        boolean existsInNacos = nacosRegistrationService.isServiceExists(serviceName);
        
        // Also check if json file exists (local double check)
        java.io.File projectFile = new java.io.File("virtual-projects", projectName.trim() + ".json");
        boolean existsLocal = projectFile.exists();
        
        if (existsInNacos || existsLocal) {
            return ResponseEntity.ok(Map.of(
                "exists", true, 
                "message", "虚拟项目已存在 (Service: " + serviceName + ")"
            ));
        } else {
            return ResponseEntity.ok(Map.of("exists", false, "message", "名称可用"));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitProject(@RequestBody com.pajk.mcpmetainfo.core.model.wizard.VirtualProjectConfig config) {
        log.info("收到项目提交请求: {}", config.getProjectName());
        try {
            // Check existence again before saving
            String serviceName = "virtual-" + config.getProjectName().trim().toLowerCase().replace(" ", "-");
            
            // Only check constraints if we want to enforce unique names for new projects
            // But since this is a wizard, we might be overwriting.
            // User requirement: "if virtual- + project name = existing node name, report error."
            // This implies rigorous check.
            boolean existsInNacos = nacosRegistrationService.isServiceExists(serviceName);
            boolean existsLocal = new java.io.File("virtual-projects", config.getProjectName().trim() + ".json").exists();
            
            // If it exists, we return error as per requirement
            if (existsInNacos || existsLocal) {
                 // But wait, if users want to update? 
                 // The prompt specifically talks about "Creating".
                 // I will treat it as a restriction for now. 
                 // If the user wants to update, they might need a different flow or "force" flag.
                 // For now, I will return error 409 Conflict if it exists.
                 // Note: The UI might need to handle this.
                 
                 // However, "submit" is usually final.
                 // Let's assume the user doesn't want to overwrite blindly.
                 return ResponseEntity.status(409).body(Map.of(
                     "success", false,
                     "message", "项目名称已存在，请使用其他名称或联系管理员删除旧项目。"
                 ));
            }

            // 保存至文件
            java.io.File projectsDir = new java.io.File("virtual-projects");
            if (!projectsDir.exists()) {
                projectsDir.mkdirs();
            }
            
            // ✅ 处理工具列表：自动添加 parameterTypes 字段
            enrichToolsWithParameterTypes(config);
            
            // 使用 Jackson ObjectMapper
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
            
            java.io.File projectFile = new java.io.File(projectsDir, config.getProjectName() + ".json");
            mapper.writeValue(projectFile, config);
            
            log.info("项目已保存至: {}", projectFile.getAbsolutePath());

            
            // 直接注册到 Nacos (根据用户反馈：提交即生效)
            try {
                // 调用服务进行注册
                nacosRegistrationService.registerVirtualProject(config);
                
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "项目已成功保存并注册至 Nacos",
                    "path", projectFile.getAbsolutePath()
                ));
            } catch (Exception nacosEx) {
                log.error("Nacos 注册失败", nacosEx);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "项目已保存，但 Nacos 注册失败: " + nacosEx.getMessage(),
                    "path", projectFile.getAbsolutePath(),
                    "warning", "Nacos 注册失败"
                ));
            }
        } catch (Exception e) {
            log.error("项目保存失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "保存失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 为工具列表自动填充 parameterTypes 字段
     * 解决虚拟项目调用 Dubbo 服务时 Integer/Long 类型不匹配的问题
     */
    private void enrichToolsWithParameterTypes(com.pajk.mcpmetainfo.core.model.wizard.VirtualProjectConfig config) {
        if (config.getTools() == null || config.getTools().isEmpty()) {
            return;
        }

        log.info("开始为虚拟项目 {} 的 {} 个工具填充参数类型...", config.getProjectName(), config.getTools().size());

        for (Map<String, Object> tool : config.getTools()) {
            try {
                String interfaceName = (String) tool.get("interfaceName");
                String methodName = (String) tool.get("methodName");

                if (interfaceName != null && methodName != null) {
                    // 尝试获取参数类型
                    java.util.List<String> parameterTypes = mcpToolSchemaGenerator.getParameterTypes(interfaceName, methodName);
                    
                    if (parameterTypes != null && !parameterTypes.isEmpty()) {
                        tool.put("parameterTypes", parameterTypes);
                        log.info("✅ 已为工具 {} 填充参数类型: {}", tool.get("toolName"), parameterTypes);
                    } else {
                        log.warn("⚠️ 未找到工具 {} 的参数类型信息", tool.get("toolName"));
                    }
                }
            } catch (Exception e) {
                log.error("填充参数类型失败: tool={}", tool.get("toolName"), e);
            }
        }
    }

    @PostMapping("/check-providers")
    public Map<String, ProviderStatus> checkProviders(@RequestBody CheckProvidersRequest request) {
        Map<String, ProviderStatus> result = new java.util.HashMap<>();
        if (request.getInterfaces() == null || request.getInterfaces().isEmpty()) {
            return result;
        }

        String basePath = "/dubbo"; // Default
        try {
            if (zkService.getConfig() != null) {
                basePath = zkService.getConfig().getBasePath();
            }
        } catch (Exception e) {
            log.warn("Failed to get ZK config, using default /dubbo", e);
        }
        
        for (String iface : request.getInterfaces()) {
            ProviderStatus status = new ProviderStatus();
            status.setOnline(false);
            status.setCount(0);
            
            try {
                String path = basePath + "/" + iface + "/providers";
                if (zkService.getClient().checkExists().forPath(path) != null) {
                    java.util.List<String> children = zkService.getClient().getChildren().forPath(path);
                    if (children != null && !children.isEmpty()) {
                        status.setOnline(true);
                        status.setCount(children.size());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to check provider for {}", iface);
            }
            result.put(iface, status);
        }
        return result;
    }

    @lombok.Data
    public static class CheckProvidersRequest {
        private java.util.List<String> interfaces;
    }
    
    @lombok.Data
    public static class ProviderStatus {
        private boolean online;
        private int count;
    }

    @lombok.Data
    public static class GitMetaRequest {
        private String gitUrl;
        private String branch;
        private String token;
    }
}
