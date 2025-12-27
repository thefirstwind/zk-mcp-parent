package com.pajk.provider2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ImportResource;

import java.util.concurrent.CountDownLatch;

/**
 * Demo Provider 2 启动类 (使用 Alibaba Dubbo 2.5)
 * 注意：使用 @Service 注解方式注册服务，不需要 XML 配置
 */
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.pajk.provider2")
@ImportResource("classpath:dubbo-provider.xml")
public class DemoProvider2Application {

    private static final CountDownLatch latch = new CountDownLatch(1);

    public static void main(String[] args) {
        try {
            SpringApplication.run(DemoProvider2Application.class, args);
            log.info("Demo Provider 2 Application (Dubbo 2.5) started successfully!");
            log.info("Services registered to ZooKeeper:");
            log.info("- UserService (com.pajk.provider2.service.UserService)");
            log.info("- OrderService (com.pajk.provider2.service.OrderService)");
            log.info("- ProductService (com.pajk.provider2.service.ProductService)");
            log.info("Application is running. Press Ctrl+C to exit.");
            
            // 保持进程运行，等待中断信号
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Demo Provider 2 Application...");
                latch.countDown();
            }));
            
            // 阻塞主线程，保持进程运行
            latch.await();
        } catch (InterruptedException e) {
            log.info("Application interrupted, shutting down...");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to start Demo Provider 2 Application", e);
            System.exit(1);
        }
    }
}

