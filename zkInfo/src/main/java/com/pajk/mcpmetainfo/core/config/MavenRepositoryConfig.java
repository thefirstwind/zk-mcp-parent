package com.pajk.mcpmetainfo.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maven 私有仓库配置（企业级）
 * 
 * 用于配置企业内部的 Nexus/Artifactory 等私有 Maven 仓库
 * 
 * 配置示例（application.yml）:
 * <pre>
 * # 本地开发环境（读取 ~/.m2/settings.xml）
 * maven:
 *   use-settings-xml: true
 * 
 * # 生产环境（显式配置）
 * maven:
 *   use-settings-xml: false
 *   nexus:
 *     url: ${NEXUS_URL:http://nexus.company.com/repository/maven-public/}
 *     username: ${NEXUS_USERNAME:}
 *     password: ${NEXUS_PASSWORD:}
 *     connect-timeout: 5000
 *     read-timeout: 30000
 * </pre>
 * 
 * 环境变量示例:
 * <pre>
 * export NEXUS_URL="http://nexus.company.com/repository/maven-public/"
 * export NEXUS_USERNAME="build-user"
 * export NEXUS_PASSWORD="your-secure-password"
 * </pre>
 * 
 * @author ZkInfo Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "maven")
public class MavenRepositoryConfig {
    
    /**
     * 是否使用 ~/.m2/settings.xml 配置
     * 本地开发：true
     * 生产环境：false（使用明确配置）
     */
    private boolean useSettingsXml = true;
    
    /**
     * Nexus 私有仓库配置
     */
    private NexusConfig nexus = new NexusConfig();
    
    /**
     * Nexus 配置
     */
    @Data
    public static class NexusConfig {
        /**
         * Nexus 仓库 URL
         * 示例: http://localhost:8881/repository/maven-public/
         */
        private String url;
        
        /**
         * 用户名（可选，私有仓库需要）
         * 建议使用环境变量：${NEXUS_USERNAME:}
         */
        private String username;
        
        /**
         * 密码（可选，私有仓库需要）
         * 建议使用环境变量：${NEXUS_PASSWORD:}
         */
        private String password;
        
        /**
         * 连接超时（毫秒）
         * 默认：5000ms
         */
        private int connectTimeout = 5000;
        
        /**
         * 读取超时（毫秒）
         * 默认：30000ms
         */
        private int readTimeout = 30000;
    }
    
    /**
     * 是否配置了 Nexus
     */
    public boolean hasNexusConfig() {
        return nexus != null && nexus.getUrl() != null && !nexus.getUrl().isEmpty();
    }
}
