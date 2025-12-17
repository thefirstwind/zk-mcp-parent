package com.zkinfo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.autoconfigure.McpWebFluxServerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ZkInfo应用启动类
 */
@Slf4j
@SpringBootApplication(exclude = {McpWebFluxServerAutoConfiguration.class})
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties
public class ZkInfoApplication {
    
    public static void main(String[] args) {
        try {
            // 设置系统属性
            System.setProperty("java.awt.headless", "true");
            System.setProperty("file.encoding", "UTF-8");
            
            // 启动Spring Boot应用
            SpringApplication app = new SpringApplication(ZkInfoApplication.class);
            
            // 设置默认配置
            app.setAdditionalProfiles("dev");
            
            // 启动应用
            var context = app.run(args);
            
            // 打印启动信息
            String serverPort = context.getEnvironment().getProperty("server.port", "8080");
            String contextPath = context.getEnvironment().getProperty("server.servlet.context-path", "");
            String zkConnectString = context.getEnvironment().getProperty("zookeeper.connect-string", "localhost:2181");
            
            log.info("=================================================================");
            log.info("    ZkInfo 应用启动成功!");
            log.info("    访问地址: http://localhost:{}{}", serverPort, contextPath);
            log.info("    健康检查: http://localhost:{}{}/actuator/health", serverPort, contextPath);
            log.info("    ZooKeeper: {}", zkConnectString);
            log.info("=================================================================");
            
        } catch (Exception e) {
            log.error("应用启动失败", e);
            System.exit(1);
        }
    }
}
