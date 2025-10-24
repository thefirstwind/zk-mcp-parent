package com.zkinfo.demo;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Demo Provider 启动类
 */
@Slf4j
@EnableDubbo
@SpringBootApplication
public class DemoProviderApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(DemoProviderApplication.class, args);
            log.info("Demo Provider Application started successfully!");
            log.info("Services registered to ZooKeeper:");
            log.info("- UserService (com.zkinfo.demo.service.UserService)");
            log.info("- OrderService (com.zkinfo.demo.service.OrderService)");
            log.info("- ProductService (com.zkinfo.demo.service.ProductService)");
        } catch (Exception e) {
            log.error("Failed to start Demo Provider Application", e);
            System.exit(1);
        }
    }
}





