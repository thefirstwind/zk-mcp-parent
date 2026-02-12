package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Git 仓库配置
 * 支持 GitHub 和 GitLab
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitRepositoryConfig {
    
    /**
     * Git 平台类型
     */
    public enum GitPlatform {
        GITHUB,
        GITLAB,
        GITEE,      // 国内用户可能使用 Gitee
        CUSTOM      // 自定义 Git 服务器
    }
    
    /**
     * Git 平台
     */
    @JsonProperty("platform")
    private GitPlatform platform;
    
    /**
     * 仓库 URL
     * 示例:
     * - https://github.com/username/repo.git
     * - https://gitlab.com/username/repo.git
     * - https://gitlab.company.com/group/project.git
     */
    @JsonProperty("repositoryUrl")
    private String repositoryUrl;
    
    /**
     * 分支名称
     * 默认: main (GitHub) 或 master (GitLab)
     */
    @Builder.Default
    @JsonProperty("branch")
    private String branch = "main";
    
    /**
     * 访问令牌（Personal Access Token）
     * - GitHub: Settings > Developer settings > Personal access tokens
     * - GitLab: User Settings > Access Tokens
     */
    @JsonProperty("accessToken")
    private String accessToken;
    
    /**
     * 用户名（用于 HTTP 基本认证）
     * 如果提供了 accessToken，通常不需要
     */
    @JsonProperty("username")
    private String username;
    
    /**
     * 密码（用于 HTTP 基本认证）
     * 不推荐使用密码，建议使用 accessToken
     */
    @JsonProperty("password")
    private String password;
    
    /**
     * 是否是私有仓库
     */
    @Builder.Default
    @JsonProperty("privateRepository")
    private boolean privateRepository = false;
    
    /**
     * 克隆深度（shallow clone）
     * 1 表示只克隆最新提交，可加快速度
     * null 表示完整克隆
     */
    @Builder.Default
    @JsonProperty("cloneDepth")
    private Integer cloneDepth = 1;
    
    /**
     * 超时时间（秒）
     */
    @Builder.Default
    @JsonProperty("timeoutSeconds")
    private int timeoutSeconds = 300;
    
    /**
     * 根据 URL 自动检测 Git 平台
     */
    public static GitPlatform detectPlatform(String url) {
        if (url == null) {
            return GitPlatform.CUSTOM;
        }
        
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("github.com")) {
            return GitPlatform.GITHUB;
        } else if (lowerUrl.contains("gitlab.com") || lowerUrl.contains("gitlab")) {
            return GitPlatform.GITLAB;
        } else if (lowerUrl.contains("gitee.com")) {
            return GitPlatform.GITEE;
        }
        
        return GitPlatform.CUSTOM;
    }
    
    /**
     * 根据平台获取默认分支
     */
    public static String getDefaultBranch(GitPlatform platform) {
        switch (platform) {
            case GITHUB:
            case GITEE:
                return "main";
            case GITLAB:
            case CUSTOM:
            default:
                return "master";
        }
    }
}
