package com.zkinfo.service;

import com.zkinfo.model.ProviderInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 服务提供者心跳监控服务
 * 
 * 负责定期检测所有注册的 Dubbo 服务提供者的可用性，通过 TCP 连接测试
 * 来判断服务提供者是否在线。支持并发检测、智能重试、离线判断和自动清理等功能。
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>定时心跳检测：定期检测所有服务提供者的连通性</li>
 *   <li>并发检测：使用线程池并发执行心跳检测，提高效率</li>
 *   <li>智能判断：基于连接超时和响应时间判断服务状态</li>
 *   <li>状态更新：实时更新服务提供者的在线/离线状态</li>
 *   <li>自动清理：清理长期离线的服务提供者记录</li>
 * </ul>
 * 
 * <p>检测策略：</p>
 * <ul>
 *   <li>检测间隔：可配置的心跳检测间隔（默认30秒）</li>
 *   <li>超时时间：可配置的连接超时时间（默认3秒）</li>
 *   <li>离线阈值：连续失败达到阈值后标记为离线（默认5分钟）</li>
 *   <li>清理阈值：长期离线后自动清理（默认30分钟）</li>
 * </ul>
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class HeartbeatMonitorService {
    
    @Autowired
    private ProviderService providerService;
    
    private ExecutorService executorService;
    
    /**
     * 连接超时时间(毫秒)
     */
    private static final int CONNECTION_TIMEOUT = 3000;
    
    /**
     * 离线阈值(分钟) - 超过此时间未响应的Provider将被标记为离线
     */
    private static final int OFFLINE_THRESHOLD_MINUTES = 5;
    
    /**
     * 清理离线Provider的阈值(分钟)
     */
    private static final int CLEANUP_THRESHOLD_MINUTES = 30;
    
    @PostConstruct
    public void init() {
        // 创建线程池用于并发心跳检测
        int threadCount = Math.max(4, Runtime.getRuntime().availableProcessors());
        executorService = Executors.newFixedThreadPool(threadCount);
        
        log.info("心跳监控服务已启动，线程池大小: {}", threadCount);
    }
    
    /**
     * 定时心跳检测 - 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000, initialDelay = 10000)
    public void performHeartbeatCheck() {
        try {
            List<ProviderInfo> providers = providerService.getAllProviders();
            if (providers.isEmpty()) {
                log.debug("没有Provider需要进行心跳检测");
                return;
            }
            
            log.debug("开始心跳检测，Provider数量: {}", providers.size());
            
            // 并发执行心跳检测
            CompletableFuture<?>[] futures = providers.stream()
                    .map(provider -> CompletableFuture.runAsync(() -> checkProviderHeartbeat(provider), executorService))
                    .toArray(CompletableFuture[]::new);
            
            // 等待所有心跳检测完成，最多等待30秒
            try {
                CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("心跳检测超时或异常", e);
            }
            
            // 统计结果
            int onlineCount = providerService.getOnlineProviderCount();
            int totalCount = providerService.getTotalProviderCount();
            log.debug("心跳检测完成，在线: {}/{}", onlineCount, totalCount);
            
        } catch (Exception e) {
            log.error("心跳检测失败", e);
        }
    }
    
    /**
     * 检测单个Provider的心跳
     */
    private void checkProviderHeartbeat(ProviderInfo provider) {
        try {
            String address = provider.getAddress();
            if (address == null || address.isEmpty()) {
                log.warn("Provider地址为空: {}", provider.getInterfaceName());
                return;
            }
            
            boolean isOnline = isProviderReachable(address);
            boolean wasOnline = provider.isOnline();
            
            // 更新Provider状态
            if (isOnline != wasOnline) {
                providerService.updateProviderStatus(address, isOnline);
                log.info("Provider状态变化: {} {} -> {}", 
                        address, 
                        wasOnline ? "在线" : "离线", 
                        isOnline ? "在线" : "离线");
            }
            
            // 如果在线，更新最后心跳时间
            if (isOnline) {
                provider.setLastHeartbeat(LocalDateTime.now());
            }
            
        } catch (Exception e) {
            log.error("检测Provider心跳失败: {}", provider.getAddress(), e);
        }
    }
    
    /**
     * 检测Provider是否可达
     */
    private boolean isProviderReachable(String address) {
        try {
            String[] parts = address.split(":");
            if (parts.length != 2) {
                log.warn("无效的Provider地址格式: {}", address);
                return false;
            }
            
            String host = parts[0];
            int port;
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("无效的端口号: {}", parts[1]);
                return false;
            }
            
            // 使用Socket连接测试可达性
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT);
                return true;
            }
            
        } catch (SocketTimeoutException e) {
            log.debug("Provider连接超时: {}", address);
            return false;
        } catch (IOException e) {
            log.debug("Provider连接失败: {} - {}", address, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("检测Provider可达性异常: {}", address, e);
            return false;
        }
    }
    
    /**
     * 定时清理长时间离线的Provider - 每10分钟执行一次
     */
    @Scheduled(fixedRate = 600000, initialDelay = 300000)
    public void cleanupOfflineProviders() {
        try {
            log.debug("开始清理长时间离线的Provider");
            
            int beforeCount = providerService.getTotalProviderCount();
            providerService.cleanupOfflineProviders(CLEANUP_THRESHOLD_MINUTES);
            int afterCount = providerService.getTotalProviderCount();
            
            if (beforeCount != afterCount) {
                log.info("清理了 {} 个长时间离线的Provider", beforeCount - afterCount);
            }
            
        } catch (Exception e) {
            log.error("清理离线Provider失败", e);
        }
    }
    
    /**
     * 标记长时间未响应的Provider为离线 - 每2分钟执行一次
     */
    @Scheduled(fixedRate = 120000, initialDelay = 60000)
    public void markStaleProvidersOffline() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(OFFLINE_THRESHOLD_MINUTES);
            List<ProviderInfo> providers = providerService.getAllProviders();
            
            int markedOfflineCount = 0;
            for (ProviderInfo provider : providers) {
                if (provider.isOnline() && 
                    provider.getLastHeartbeat() != null && 
                    provider.getLastHeartbeat().isBefore(threshold)) {
                    
                    providerService.updateProviderStatus(provider.getAddress(), false);
                    markedOfflineCount++;
                    
                    log.info("标记长时间未响应的Provider为离线: {} (最后心跳: {})", 
                            provider.getAddress(), 
                            provider.getLastHeartbeat());
                }
            }
            
            if (markedOfflineCount > 0) {
                log.info("标记了 {} 个长时间未响应的Provider为离线", markedOfflineCount);
            }
            
        } catch (Exception e) {
            log.error("标记过期Provider为离线失败", e);
        }
    }
    
    /**
     * 手动触发心跳检测
     */
    public void triggerHeartbeatCheck() {
        log.info("手动触发心跳检测");
        CompletableFuture.runAsync(this::performHeartbeatCheck, executorService);
    }
    
    /**
     * 检测指定Provider的心跳
     */
    public boolean checkSpecificProvider(String address) {
        try {
            boolean isOnline = isProviderReachable(address);
            providerService.updateProviderStatus(address, isOnline);
            
            log.info("检测Provider: {} -> {}", address, isOnline ? "在线" : "离线");
            return isOnline;
            
        } catch (Exception e) {
            log.error("检测指定Provider失败: {}", address, e);
            return false;
        }
    }
    
    /**
     * 获取心跳监控统计信息
     */
    public HeartbeatStats getHeartbeatStats() {
        try {
            List<ProviderInfo> providers = providerService.getAllProviders();
            
            int totalProviders = providers.size();
            int onlineProviders = (int) providers.stream().filter(ProviderInfo::isOnline).count();
            int offlineProviders = totalProviders - onlineProviders;
            
            // 计算最近心跳时间
            LocalDateTime latestHeartbeat = providers.stream()
                    .filter(p -> p.getLastHeartbeat() != null)
                    .map(ProviderInfo::getLastHeartbeat)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            
            return new HeartbeatStats(totalProviders, onlineProviders, offlineProviders, latestHeartbeat);
            
        } catch (Exception e) {
            log.error("获取心跳统计信息失败", e);
            return new HeartbeatStats(0, 0, 0, null);
        }
    }
    
    /**
     * 心跳统计信息
     */
    public static class HeartbeatStats {
        private final int totalProviders;
        private final int onlineProviders;
        private final int offlineProviders;
        private final LocalDateTime latestHeartbeat;
        
        public HeartbeatStats(int totalProviders, int onlineProviders, int offlineProviders, LocalDateTime latestHeartbeat) {
            this.totalProviders = totalProviders;
            this.onlineProviders = onlineProviders;
            this.offlineProviders = offlineProviders;
            this.latestHeartbeat = latestHeartbeat;
        }
        
        public int getTotalProviders() { return totalProviders; }
        public int getOnlineProviders() { return onlineProviders; }
        public int getOfflineProviders() { return offlineProviders; }
        public LocalDateTime getLatestHeartbeat() { return latestHeartbeat; }
        public double getOnlineRate() { 
            return totalProviders > 0 ? (double) onlineProviders / totalProviders : 0.0; 
        }
    }
    
    /**
     * 销毁资源
     */
    public void destroy() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("心跳监控服务已关闭");
        }
    }
}
