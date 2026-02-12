package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.wizard.GitRepositoryConfig;
import com.pajk.mcpmetainfo.core.model.wizard.PomParseProgress;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * Git 仓库服务
 * 支持 GitHub 和 GitLab 的仓库克隆和管理
 */
@Slf4j
@Service
public class GitRepositoryService {
    
    private static final String TEMP_DIR = System.getProperty("java.io.tmpdir") + "/zkinfo-git";
    
    /**
     * 克隆 Git 仓库
     *
     * @param config Git 仓库配置
     * @param progress 进度对象
     * @param progressCallback 进度回调
     * @return 克隆后的本地路径
     */
    public File cloneRepository(
            GitRepositoryConfig config,
            PomParseProgress progress,
            Consumer<PomParseProgress> progressCallback
    ) throws Exception {
        
        // 自动检测平台（如果未指定）
        if (config.getPlatform() == null) {
            GitRepositoryConfig.GitPlatform platform = GitRepositoryConfig.detectPlatform(config.getRepositoryUrl());
            config.setPlatform(platform);
            log.info("自动检测到 Git 平台: {}", platform);
        }
        
        // 自动设置默认分支（如果未指定）
        if (config.getBranch() == null || config.getBranch().isEmpty()) {
            String defaultBranch = GitRepositoryConfig.getDefaultBranch(config.getPlatform());
            config.setBranch(defaultBranch);
            log.info("使用默认分支: {}", defaultBranch);
        }
        
        progress.addLog(String.format("开始克隆仓库: %s", config.getRepositoryUrl()));
        progress.addLog(String.format("平台: %s, 分支: %s", config.getPlatform(), config.getBranch()));
        progressCallback.accept(progress);
        
        // 生成本地目录名
        String repoName = extractRepoName(config.getRepositoryUrl());
        Path localPath = Paths.get(TEMP_DIR, repoName + "_" + System.currentTimeMillis());
        Files.createDirectories(localPath);
        
        progress.addLog(String.format("本地路径: %s", localPath));
        progressCallback.accept(progress);
        
        try {
            // 配置克隆命令
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(config.getRepositoryUrl())
                    .setDirectory(localPath.toFile())
                    .setBranch(config.getBranch());
            
            // 设置克隆深度（加快速度）
            if (config.getCloneDepth() != null && config.getCloneDepth() > 0) {
                cloneCommand.setDepth(config.getCloneDepth());
                progress.addLog(String.format("浅克隆深度: %d", config.getCloneDepth()));
            }
            
            // 配置认证
            if (config.getAccessToken() != null && !config.getAccessToken().isEmpty()) {
                // 使用 Personal Access Token
                // GitHub: token 作为密码，用户名可以是任意非空字符串
                // GitLab: token 作为密码，用户名通常是 "oauth2" 或 "private-token"
                String username = determineUsername(config);
                cloneCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(username, config.getAccessToken())
                );
                progress.addLog("使用 Access Token 认证");
            } else if (config.getUsername() != null && config.getPassword() != null) {
                // 使用用户名密码（不推荐）
                cloneCommand.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(config.getUsername(), config.getPassword())
                );
                progress.addLog("使用用户名/密码认证");
            } else if (config.isPrivateRepository()) {
                progress.addLog("⚠️ 私有仓库但未提供认证信息，可能会失败");
            }
            
            progressCallback.accept(progress);
            
            // 执行克隆
            progress.addLog("正在克隆...");
            progressCallback.accept(progress);
            
            try (Git git = cloneCommand.call()) {
                progress.addLog(String.format("✅ 克隆成功: %s", repoName));
                progressCallback.accept(progress);
                
                return localPath.toFile();
            }
            
        } catch (Exception e) {
            log.error("克隆仓库失败", e);
            progress.addLog(String.format("❌ 克隆失败: %s", e.getMessage()));
            
            // 提供针对不同平台的错误提示
            String errorHint = getErrorHint(config.getPlatform(), e);
            if (errorHint != null) {
                progress.addLog(errorHint);
            }
            
            progressCallback.accept(progress);
            
            // 清理失败的目录
            try {
                if (Files.exists(localPath)) {
                    deleteDirectory(localPath.toFile());
                }
            } catch (Exception cleanupEx) {
                log.warn("清理失败目录时出错", cleanupEx);
            }
            
            throw e;
        }
    }
    
    /**
     * 根据平台确定用户名
     */
    private String determineUsername(GitRepositoryConfig config) {
        switch (config.getPlatform()) {
            case GITHUB:
            case GITEE:
                // GitHub 和 Gitee 使用 token 时，用户名可以是任意非空字符串
                return config.getUsername() != null ? config.getUsername() : "oauth2";
                
            case GITLAB:
                // GitLab 推荐使用特定用户名
                return config.getUsername() != null ? config.getUsername() : "oauth2";
                
            case CUSTOM:
            default:
                return config.getUsername() != null ? config.getUsername() : "git";
        }
    }
    
    /**
     * 根据平台提供错误提示
     */
    private String getErrorHint(GitRepositoryConfig.GitPlatform platform, Exception e) {
        String message = e.getMessage().toLowerCase();
        
        if (message.contains("authentication") || message.contains("401") || message.contains("403")) {
            switch (platform) {
                case GITHUB:
                    return "提示: GitHub 需要 Personal Access Token (Settings > Developer settings > Personal access tokens)";
                case GITLAB:
                    return "提示: GitLab 需要 Personal Access Token (User Settings > Access Tokens)";
                case GITEE:
                    return "提示: Gitee 需要私人令牌 (设置 > 安全设置 > 私人令牌)";
            }
        }
        
        if (message.contains("not found") || message.contains("404")) {
            return "提示: 仓库不存在或无访问权限，请检查 URL 和认证信息";
        }
        
        return null;
    }
    
    /**
     * 从 URL 提取仓库名称
     */
    private String extractRepoName(String url) {
        // https://github.com/username/repo.git -> repo
        // https://gitlab.com/group/project.git -> project
        String[] parts = url.split("/");
        String lastPart = parts[parts.length - 1];
        return lastPart.replace(".git", "").replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
    
    /**
     * 清理临时目录
     */
    public void cleanupTempDirectory(File directory) {
        try {
            if (directory != null && directory.exists()) {
                deleteDirectory(directory);
                log.info("清理临时目录: {}", directory.getPath());
            }
        } catch (Exception e) {
            log.warn("清理临时目录失败: {}", directory.getPath(), e);
        }
    }
}
