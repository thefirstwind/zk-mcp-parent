package com.zkinfo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 服务采集过滤服务
 * 
 * 实现三层过滤机制：
 * 1. 项目级过滤：只采集已定义项目包含的服务
 * 2. 服务级过滤：通过过滤规则配置（包含/排除）
 * 3. 审批级过滤：只有审批通过的服务才会被采集
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@Service
public class ServiceCollectionFilterService {
    
    private ProjectManagementService projectManagementService;
    
    @Autowired(required = false)
    @Lazy
    public void setProjectManagementService(ProjectManagementService projectManagementService) {
        this.projectManagementService = projectManagementService;
    }
    
    @Value("${service.filter.enabled:true}")
    private boolean filterEnabled;
    
    @Value("${service.filter.require-approval:true}")
    private boolean requireApproval;
    
    // 项目服务缓存：serviceInterface:version -> projectIds
    private final Map<String, Set<Long>> projectServiceCache = new ConcurrentHashMap<>();
    
    // 已审批服务缓存：serviceInterface:version -> approvalId
    private final Map<String, Long> approvedServicesCache = new ConcurrentHashMap<>();
    
    // 过滤规则缓存
    private final List<FilterRule> filterRules = new ArrayList<>();
    
    // ServiceCollectionFilter模型缓存：id -> ServiceCollectionFilter
    private final Map<Long, com.zkinfo.model.ServiceCollectionFilter> filterCache = new ConcurrentHashMap<>();
    private final AtomicLong filterIdGenerator = new AtomicLong(1);
    
    /**
     * 判断服务是否应该被采集
     * 
     * @param serviceInterface 服务接口名
     * @param version 服务版本
     * @param group 服务分组（可选）
     * @return true表示应该采集，false表示应该过滤掉
     */
    public boolean shouldCollect(String serviceInterface, String version, String group) {
        if (!filterEnabled) {
            log.debug("Service filter is disabled, collect all services");
            return true;
        }
        
        // 1. 项目级过滤：检查是否在已定义的项目中
        if (!isInDefinedProjects(serviceInterface, version)) {
            log.debug("Service {}/{} is not in any defined project, filtered out", 
                    serviceInterface, version);
            return false;
        }
        
        // 2. 服务级过滤：检查过滤规则
        if (isFilteredOut(serviceInterface, version, group)) {
            log.debug("Service {}/{} is filtered out by filter rules", 
                    serviceInterface, version);
            return false;
        }
        
        // 3. 审批级过滤：检查审批状态（如果启用）
        if (requireApproval && !isApproved(serviceInterface, version)) {
            log.debug("Service {}/{} is not approved, filtered out", 
                    serviceInterface, version);
            return false;
        }
        
        log.debug("Service {}/{} passed all filters, should be collected", 
                serviceInterface, version);
        return true;
    }
    
    /**
     * 检查服务是否在已定义的项目中
     * 
     * @param serviceInterface 服务接口名
     * @param version 服务版本
     * @return true表示在项目中，false表示不在
     */
    private boolean isInDefinedProjects(String serviceInterface, String version) {
        // 优先使用ProjectManagementService检查
        if (projectManagementService != null) {
            boolean inProject = projectManagementService.isServiceInAnyProject(serviceInterface, version);
            if (inProject) {
                return true;
            }
        }
        
        // 回退到缓存检查
        String key = buildServiceKey(serviceInterface, version);
        Set<Long> projectIds = projectServiceCache.get(key);
        
        if (projectIds == null || projectIds.isEmpty()) {
            // 如果缓存中没有，且没有项目管理服务，暂时返回true（不强制要求项目关联）
            // TODO: 实现数据库查询逻辑后，这里应该返回false
            return true; // 暂时允许所有服务，等待项目数据初始化
        }
        
        return !projectIds.isEmpty();
    }
    
    /**
     * 检查是否被过滤规则排除
     * 
     * @param serviceInterface 服务接口名
     * @param version 服务版本
     * @param group 服务分组
     * @return true表示被排除，false表示不被排除
     */
    private boolean isFilteredOut(String serviceInterface, String version, String group) {
        if (filterRules.isEmpty()) {
            return false;
        }
        
        // 按优先级排序（数字越大优先级越高）
        List<FilterRule> sortedRules = new ArrayList<>(filterRules);
        sortedRules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        
        for (FilterRule rule : sortedRules) {
            if (!rule.isEnabled()) {
                continue;
            }
            
            if (matchesFilter(serviceInterface, version, group, rule)) {
                // 匹配到规则，根据操作符决定是否排除
                return "EXCLUDE".equalsIgnoreCase(rule.getOperator());
            }
        }
        
        return false;
    }
    
    /**
     * 检查服务是否匹配过滤规则
     */
    private boolean matchesFilter(String serviceInterface, String version, String group, FilterRule rule) {
        switch (rule.getType()) {
            case "PROJECT":
                // 项目级过滤：检查服务是否在指定项目中
                String key = buildServiceKey(serviceInterface, version);
                Set<Long> projectIds = projectServiceCache.get(key);
                if (projectIds != null) {
                    // 检查是否包含指定的项目ID
                    try {
                        Long projectId = Long.parseLong(rule.getValue());
                        return projectIds.contains(projectId);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid project ID in filter rule: {}", rule.getValue());
                    }
                }
                return false;
                
            case "SERVICE":
                // 服务级过滤：精确匹配服务接口名
                return serviceInterface.equals(rule.getValue());
                
            case "PATTERN":
                // 模式匹配：使用正则表达式
                try {
                    Pattern pattern = Pattern.compile(rule.getValue());
                    return pattern.matcher(serviceInterface).matches() ||
                           pattern.matcher(version != null ? version : "").matches();
                } catch (Exception e) {
                    log.warn("Invalid pattern in filter rule: {}", rule.getValue(), e);
                    return false;
                }
                
            case "PREFIX":
                // 前缀匹配
                return serviceInterface.startsWith(rule.getValue());
                
            case "SUFFIX":
                // 后缀匹配
                return serviceInterface.endsWith(rule.getValue());
                
            default:
                log.warn("Unknown filter type: {}", rule.getType());
                return false;
        }
    }
    
    /**
     * 检查服务是否已审批通过
     * 
     * @param serviceInterface 服务接口名
     * @param version 服务版本
     * @return true表示已审批，false表示未审批
     */
    private boolean isApproved(String serviceInterface, String version) {
        String key = buildServiceKey(serviceInterface, version);
        Long approvalId = approvedServicesCache.get(key);
        
        if (approvalId == null) {
            // 如果缓存中没有，尝试查询数据库（如果实现了Mapper）
            // TODO: 实现数据库查询逻辑
            return false; // 默认未审批
        }
        
        return approvalId > 0;
    }
    
    /**
     * 构建服务唯一标识
     */
    private String buildServiceKey(String serviceInterface, String version) {
        return String.format("%s:%s", serviceInterface, version != null ? version : "default");
    }
    
    /**
     * 添加项目服务关联（用于初始化或更新）
     */
    public void addProjectService(Long projectId, String serviceInterface, String version) {
        String key = buildServiceKey(serviceInterface, version);
        projectServiceCache.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(projectId);
        log.debug("Added project service: projectId={}, service={}", projectId, key);
    }
    
    /**
     * 移除项目服务关联
     */
    public void removeProjectService(Long projectId, String serviceInterface, String version) {
        String key = buildServiceKey(serviceInterface, version);
        Set<Long> projectIds = projectServiceCache.get(key);
        if (projectIds != null) {
            projectIds.remove(projectId);
            if (projectIds.isEmpty()) {
                projectServiceCache.remove(key);
            }
        }
        log.debug("Removed project service: projectId={}, service={}", projectId, key);
    }
    
    /**
     * 标记服务为已审批
     */
    public void markServiceAsApproved(String serviceInterface, String version, Long approvalId) {
        String key = buildServiceKey(serviceInterface, version);
        approvedServicesCache.put(key, approvalId);
        log.debug("Marked service as approved: service={}, approvalId={}", key, approvalId);
    }
    
    /**
     * 添加过滤规则
     */
    public void addFilterRule(FilterRule rule) {
        filterRules.add(rule);
        log.debug("Added filter rule: type={}, value={}, operator={}", 
                rule.getType(), rule.getValue(), rule.getOperator());
    }
    
    /**
     * 添加ServiceCollectionFilter模型
     */
    public com.zkinfo.model.ServiceCollectionFilter addFilter(com.zkinfo.model.ServiceCollectionFilter filter) {
        if (filter.getId() == null) {
            filter.setId(filterIdGenerator.getAndIncrement());
        }
        filter.setCreatedAt(java.time.LocalDateTime.now());
        filter.setUpdatedAt(java.time.LocalDateTime.now());
        
        filterCache.put(filter.getId(), filter);
        
        // 同步到FilterRule
        FilterRule rule = new FilterRule(
                filter.getFilterType().name(),
                filter.getFilterValue(),
                filter.getFilterOperator().name(),
                filter.getPriority() != null ? filter.getPriority() : 0,
                filter.getEnabled() != null && filter.getEnabled()
        );
        addFilterRule(rule);
        
        log.info("Added filter: id={}, type={}, value={}, operator={}", 
                filter.getId(), filter.getFilterType(), filter.getFilterValue(), filter.getFilterOperator());
        
        return filter;
    }
    
    /**
     * 获取所有ServiceCollectionFilter
     */
    public List<com.zkinfo.model.ServiceCollectionFilter> getAllFilters() {
        return new ArrayList<>(filterCache.values());
    }
    
    /**
     * 获取启用的ServiceCollectionFilter
     */
    public List<com.zkinfo.model.ServiceCollectionFilter> getEnabledFilters() {
        return filterCache.values().stream()
                .filter(f -> f.getEnabled() != null && f.getEnabled())
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 获取指定的ServiceCollectionFilter
     */
    public com.zkinfo.model.ServiceCollectionFilter getFilter(Long filterId) {
        return filterCache.get(filterId);
    }
    
    /**
     * 更新ServiceCollectionFilter
     */
    public void updateFilter(com.zkinfo.model.ServiceCollectionFilter filter) {
        filter.setUpdatedAt(java.time.LocalDateTime.now());
        filterCache.put(filter.getId(), filter);
        
        // 同步更新FilterRule
        filterRules.removeIf(r -> {
            // 通过匹配规则来找到对应的FilterRule（简化实现）
            return r.getType().equals(filter.getFilterType().name()) &&
                   r.getValue().equals(filter.getFilterValue());
        });
        
        FilterRule rule = new FilterRule(
                filter.getFilterType().name(),
                filter.getFilterValue(),
                filter.getFilterOperator().name(),
                filter.getPriority() != null ? filter.getPriority() : 0,
                filter.getEnabled() != null && filter.getEnabled()
        );
        addFilterRule(rule);
        
        log.info("Updated filter: id={}", filter.getId());
    }
    
    /**
     * 删除ServiceCollectionFilter
     */
    public boolean deleteFilter(Long filterId) {
        com.zkinfo.model.ServiceCollectionFilter filter = filterCache.remove(filterId);
        if (filter != null) {
            // 同步删除FilterRule
            filterRules.removeIf(r -> 
                    r.getType().equals(filter.getFilterType().name()) &&
                    r.getValue().equals(filter.getFilterValue())
            );
            log.info("Deleted filter: id={}", filterId);
            return true;
        }
        return false;
    }
    
    /**
     * 清除所有缓存
     */
    public void clearCache() {
        projectServiceCache.clear();
        approvedServicesCache.clear();
        filterRules.clear();
        log.info("Cleared all filter caches");
    }
    
    /**
     * 过滤规则内部类
     */
    public static class FilterRule {
        private String type; // PROJECT, SERVICE, PATTERN, PREFIX, SUFFIX
        private String value;
        private String operator; // INCLUDE, EXCLUDE
        private int priority;
        private boolean enabled;
        
        public FilterRule(String type, String value, String operator, int priority, boolean enabled) {
            this.type = type;
            this.value = value;
            this.operator = operator;
            this.priority = priority;
            this.enabled = enabled;
        }
        
        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}

