package com.pajk.mcpmetainfo.core.service;

import com.pajk.mcpmetainfo.core.model.ProviderInfo;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceEntity;
import com.pajk.mcpmetainfo.persistence.entity.DubboServiceNodeEntity;
import com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity;
import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceMapper;
import com.pajk.mcpmetainfo.persistence.mapper.DubboServiceNodeMapper;
import com.pajk.mcpmetainfo.persistence.mapper.ProviderInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 批量持久化服务
 * 
 * 用于优化大量数据的快速入库，支持批量插入和更新
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
public class BatchPersistenceService {
    
    @Autowired
    private DubboServiceMapper dubboServiceMapper;
    
    @Autowired
    private DubboServiceNodeMapper dubboServiceNodeMapper;
    
    @Autowired
    private ProviderInfoMapper providerInfoMapper;
    
    /**
     * 批量处理大小（每次处理的记录数）
     */
    private static final int BATCH_SIZE = 500;
    
    /**
     * 批量持久化Provider信息列表
     * 
     * @param providerInfos Provider信息列表
     * @return 处理结果统计
     */
    @Transactional
    public BatchResult batchPersistProviders(List<ProviderInfo> providerInfos) {
        if (providerInfos == null || providerInfos.isEmpty()) {
            return new BatchResult(0, 0, 0);
        }
        
        long startTime = System.currentTimeMillis();
        int totalCount = providerInfos.size();
        int successCount = 0;
        int failureCount = 0;
        
        try {
            log.info("开始批量持久化 {} 条Provider信息", totalCount);
            
            // 1. 按服务维度分组，先批量处理服务信息
            Map<String, DubboServiceEntity> serviceMap = new ConcurrentHashMap<>();
            Map<String, List<ProviderInfo>> serviceGroupMap = providerInfos.stream()
                    .collect(Collectors.groupingBy(this::getServiceKey));
            
            // 2. 批量插入或更新服务信息
            List<DubboServiceEntity> services = new ArrayList<>();
            for (Map.Entry<String, List<ProviderInfo>> entry : serviceGroupMap.entrySet()) {
                ProviderInfo firstProvider = entry.getValue().get(0);
                DubboServiceEntity service = new DubboServiceEntity(firstProvider);
                service.setProviderCount(entry.getValue().size());
                service.setOnlineProviderCount((int) entry.getValue().stream()
                        .filter(ProviderInfo::isOnline)
                        .count());
                services.add(service);
                serviceMap.put(entry.getKey(), service);
            }
            
            // 批量插入服务
            if (!services.isEmpty()) {
                batchInsertServices(services);
                // 重新查询获取ID
                for (DubboServiceEntity service : services) {
                    DubboServiceEntity found = dubboServiceMapper.findByServiceKey(
                            service.getInterfaceName(),
                            service.getProtocol(),
                            service.getVersion(),
                            service.getGroup(),
                            service.getApplication()
                    );
                    if (found != null) {
                        service.setId(found.getId());
                        serviceMap.put(getServiceKey(service), service);
                    }
                }
            }
            
            // 3. 批量处理Provider信息和节点信息
            List<ProviderInfoEntity> providerEntities = new ArrayList<>();
            List<DubboServiceNodeEntity> nodeEntities = new ArrayList<>();
            
            for (ProviderInfo providerInfo : providerInfos) {
                try {
                    String serviceKey = getServiceKey(providerInfo);
                    DubboServiceEntity service = serviceMap.get(serviceKey);
                    
                    if (service == null || service.getId() == null) {
                        log.warn("找不到对应的服务，跳过Provider: {}", providerInfo.getZkPath());
                        failureCount++;
                        continue;
                    }
                    
                    // 创建Provider实体
                    ProviderInfoEntity providerEntity = new ProviderInfoEntity(providerInfo);
                    providerEntities.add(providerEntity);
                    
                    // 创建节点实体（传入 version）
                    DubboServiceNodeEntity nodeEntity = new DubboServiceNodeEntity(
                        providerInfo, service.getId(), service.getVersion());
                    nodeEntities.add(nodeEntity);
                    
                    successCount++;
                } catch (Exception e) {
                    log.error("处理Provider失败: {}", providerInfo.getZkPath(), e);
                    failureCount++;
                }
            }
            
            // 4. 批量插入Provider信息
            if (!providerEntities.isEmpty()) {
                batchInsertProviders(providerEntities);
            }
            
            // 5. 批量插入节点信息
            if (!nodeEntities.isEmpty()) {
                batchInsertNodes(nodeEntities);
            }
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("批量持久化完成: 总数={}, 成功={}, 失败={}, 耗时={}ms, 平均={}ms/条",
                    totalCount, successCount, failureCount, duration,
                    totalCount > 0 ? duration / totalCount : 0);
            
            return new BatchResult(totalCount, successCount, failureCount);
            
        } catch (Exception e) {
            log.error("批量持久化Provider信息失败", e);
            throw new RuntimeException("批量持久化失败", e);
        }
    }
    
    /**
     * 批量插入服务信息（分批处理）
     */
    private void batchInsertServices(List<DubboServiceEntity> services) {
        if (services.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < services.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, services.size());
            List<DubboServiceEntity> batch = services.subList(i, end);
            try {
                int count = dubboServiceMapper.batchInsert(batch);
                log.debug("批量插入服务: {} 条", count);
            } catch (Exception e) {
                log.error("批量插入服务失败: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
    
    /**
     * 批量插入Provider信息（分批处理）
     */
    private void batchInsertProviders(List<ProviderInfoEntity> providers) {
        if (providers.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < providers.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, providers.size());
            List<ProviderInfoEntity> batch = providers.subList(i, end);
            try {
                int count = providerInfoMapper.batchInsert(batch);
                log.debug("批量插入Provider: {} 条", count);
            } catch (Exception e) {
                log.error("批量插入Provider失败: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
    
    /**
     * 批量插入节点信息（分批处理）
     */
    private void batchInsertNodes(List<DubboServiceNodeEntity> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < nodes.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, nodes.size());
            List<DubboServiceNodeEntity> batch = nodes.subList(i, end);
            try {
                int count = dubboServiceNodeMapper.batchInsert(batch);
                log.debug("批量插入节点: {} 条", count);
            } catch (Exception e) {
                log.error("批量插入节点失败: {}", e.getMessage(), e);
                throw e;
            }
        }
    }
    
    /**
     * 获取服务唯一标识
     */
    private String getServiceKey(ProviderInfo providerInfo) {
        return providerInfo.getInterfaceName() + ":" +
                (providerInfo.getProtocol() != null ? providerInfo.getProtocol() : "") + ":" +
                (providerInfo.getVersion() != null ? providerInfo.getVersion() : "") + ":" +
                (providerInfo.getGroup() != null ? providerInfo.getGroup() : "") + ":" +
                (providerInfo.getApplication() != null ? providerInfo.getApplication() : "");
    }
    
    private String getServiceKey(DubboServiceEntity service) {
        return service.getInterfaceName() + ":" +
                (service.getProtocol() != null ? service.getProtocol() : "") + ":" +
                (service.getVersion() != null ? service.getVersion() : "") + ":" +
                (service.getGroup() != null ? service.getGroup() : "") + ":" +
                (service.getApplication() != null ? service.getApplication() : "");
    }
    
    /**
     * 批量处理结果
     */
    public static class BatchResult {
        private final int totalCount;
        private final int successCount;
        private final int failureCount;
        
        public BatchResult(int totalCount, int successCount, int failureCount) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
        }
        
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        
        @Override
        public String toString() {
            return String.format("BatchResult{总数=%d, 成功=%d, 失败=%d}", totalCount, successCount, failureCount);
        }
    }
}

