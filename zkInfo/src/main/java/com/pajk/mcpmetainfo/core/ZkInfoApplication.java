package com.pajk.mcpmetainfo.core;

import com.pajk.mcpmetainfo.core.config.McpSessionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.server.autoconfigure.McpWebFluxServerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.ComponentScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;

/**
 * ZkInfo应用启动类
 */
@Slf4j
@SpringBootApplication(exclude = {McpWebFluxServerAutoConfiguration.class,MybatisAutoConfiguration.class})
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(McpSessionProperties.class)
@ComponentScan(basePackages = {
    "com.pajk.mcpmetainfo.core",
    "com.pajk.mcpmetainfo.persistence"
})
public class ZkInfoApplication {
    
    public static void main(String[] args) {
        try {
            // 设置系统属性
            System.setProperty("java.awt.headless", "true");
            System.setProperty("file.encoding", "UTF-8");
            
            // 检查并设置 Java 模块系统参数（用于支持 Dubbo Javassist）
            // 如果 JVM 参数中未设置，则通过系统属性设置（作为备选方案）
            // 注意：这些参数最好在启动时通过命令行设置，但这里作为备选方案
            if (System.getProperty("java.util.logging.manager") == null) {
                // 检查是否已经设置了 --add-opens
                // 如果没有，尝试通过反射设置（但这可能不会生效，因为模块系统在启动时就已经确定）
                // 所以最好还是通过 JVM 启动参数设置
            }
            
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
