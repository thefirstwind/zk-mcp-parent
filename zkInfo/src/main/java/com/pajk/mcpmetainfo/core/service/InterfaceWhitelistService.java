package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.persistence.entity.InterfaceWhitelistEntity;
import com.pajk.mcpmetainfo.persistence.mapper.InterfaceWhitelistMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 接口白名单服务
 * 
 * 用于控制哪些接口可以入库，只有 interface_name 左匹配白名单的接口才准许入库
 * 支持从配置文件和数据库两个来源获取白名单
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-25
 */
@Slf4j
@Service
public class InterfaceWhitelistService {
    
    /**
     * 接口白名单列表（从配置文件中读取，支持逗号分隔的多个前缀）
     * 例如：com.zkinfo.demo,com.example.service
     */
    @Value("${zk.whitelist.interfaces:}")
    private String whitelistInterfaces;
    
    /**
     * 是否启用数据库白名单（默认启用）
     */
    @Value("${zk.whitelist.database.enabled:true}")
    private boolean databaseWhitelistEnabled;
    
    /**
     * 白名单缓存刷新间隔（秒），默认60秒
     */
    @Value("${zk.whitelist.cache.refresh-interval:60}")
    private long cacheRefreshInterval;
    
    @Autowired(required = false)
    private InterfaceWhitelistMapper interfaceWhitelistMapper;
    
    private List<String> whitelistPrefixes = new ArrayList<>();
    private final ConcurrentHashMap<String, Long> lastRefreshTime = new ConcurrentHashMap<>();
    private ScheduledExecutorService refreshExecutor;
    
    /**
     * 初始化白名单前缀列表
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        // 1. 从配置文件加载白名单
        loadWhitelistFromConfig();
        
        // 2. 从数据库加载白名单（如果启用）
        if (databaseWhitelistEnabled && interfaceWhitelistMapper != null) {
            loadWhitelistFromDatabase();
            
            // 3. 启动定时刷新任务
            startRefreshTask();
        } else {
            log.info("数据库白名单未启用或 Mapper 未注入，仅使用配置文件白名单");
        }
        
        log.info("✅ 接口白名单已初始化，共 {} 个前缀: {}", 
                whitelistPrefixes.size(), whitelistPrefixes);
    }
    
    /**
     * 从配置文件加载白名单
     */
    private void loadWhitelistFromConfig() {
        List<String> configPrefixes = new ArrayList<>();
        if (whitelistInterfaces != null && !whitelistInterfaces.trim().isEmpty()) {
            configPrefixes = Stream.of(whitelistInterfaces.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            log.info("从配置文件加载到 {} 个白名单前缀: {}", configPrefixes.size(), configPrefixes);
        }
        
        // 合并到总列表
        whitelistPrefixes.addAll(configPrefixes);
    }
    
    /**
     * 从数据库加载白名单
     */
    private void loadWhitelistFromDatabase() {
        try {
            List<InterfaceWhitelistEntity> entities = interfaceWhitelistMapper.findAllEnabled();
            List<String> dbPrefixes = entities.stream()
                    .map(InterfaceWhitelistEntity::getPrefix)
                    .filter(prefix -> prefix != null && !prefix.trim().isEmpty())
                    .collect(Collectors.toList());
            
            log.info("从数据库加载到 {} 个启用的白名单前缀: {}", dbPrefixes.size(), dbPrefixes);
            
            // 合并到总列表（去重）
            for (String prefix : dbPrefixes) {
                if (!whitelistPrefixes.contains(prefix)) {
                    whitelistPrefixes.add(prefix);
                }
            }
        } catch (Exception e) {
            log.error("从数据库加载白名单失败，将仅使用配置文件白名单", e);
        }
    }
    
    /**
     * 启动定时刷新任务
     */
    private void startRefreshTask() {
        if (refreshExecutor != null) {
            return;
        }
        
        refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "whitelist-refresh");
            t.setDaemon(true);
            return t;
        });
        
        refreshExecutor.scheduleWithFixedDelay(() -> {
            try {
                log.debug("定时刷新白名单缓存...");
                refreshWhitelistCache();
            } catch (Exception e) {
                log.error("刷新白名单缓存失败", e);
            }
        }, cacheRefreshInterval, cacheRefreshInterval, TimeUnit.SECONDS);
        
        log.info("白名单缓存刷新任务已启动，刷新间隔: {} 秒", cacheRefreshInterval);
    }
    
    /**
     * 刷新白名单缓存
     */
    public void refreshWhitelistCache() {
        if (!databaseWhitelistEnabled || interfaceWhitelistMapper == null) {
            return;
        }
        
        // 清空现有列表（保留配置文件中的）
        List<String> configPrefixes = new ArrayList<>();
        if (whitelistInterfaces != null && !whitelistInterfaces.trim().isEmpty()) {
            configPrefixes = Stream.of(whitelistInterfaces.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        
        whitelistPrefixes = new ArrayList<>(configPrefixes);
        
        // 重新从数据库加载
        loadWhitelistFromDatabase();
        
        log.info("白名单缓存已刷新，当前共 {} 个前缀", whitelistPrefixes.size());
    }
    
    @jakarta.annotation.PreDestroy
    public void destroy() {
        if (refreshExecutor != null) {
            refreshExecutor.shutdown();
            try {
                if (!refreshExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    refreshExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                refreshExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 检查接口名是否在白名单中（左匹配）
     * 
     * @param interfaceName 接口全限定名，例如：com.zkinfo.demo.service.UserService
     * @return 如果匹配白名单返回 true，否则返回 false
     */
    public boolean isAllowed(String interfaceName) {
        if (interfaceName == null || interfaceName.trim().isEmpty()) {
            log.debug("接口名为空，不允许入库");
            return false;
        }
        
        // 如果白名单为空，允许所有接口（兼容旧行为，但会记录警告）
        if (whitelistPrefixes == null || whitelistPrefixes.isEmpty()) {
            log.debug("白名单为空，允许接口入库: {}", interfaceName);
            return true;
        }
        
        // 检查是否匹配任何一个白名单前缀（左匹配）
        for (String prefix : whitelistPrefixes) {
            if (interfaceName.startsWith(prefix)) {
                log.debug("✅ 接口 {} 匹配白名单前缀: {}", interfaceName, prefix);
                return true;
            }
        }
        
        log.debug("❌ 接口 {} 不在白名单中，不允许入库", interfaceName);
        return false;
    }
    
    /**
     * 获取所有白名单前缀
     * 
     * @return 白名单前缀列表
     */
    public List<String> getWhitelistPrefixes() {
        return whitelistPrefixes != null ? new ArrayList<>(whitelistPrefixes) : new ArrayList<>();
    }
    
    /**
     * 检查白名单是否已配置
     * 
     * @return 如果已配置返回 true，否则返回 false
     */
    public boolean isWhitelistConfigured() {
        return whitelistPrefixes != null && !whitelistPrefixes.isEmpty();
    }
}

