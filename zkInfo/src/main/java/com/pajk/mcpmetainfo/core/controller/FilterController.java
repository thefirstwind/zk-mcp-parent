package com.pajk.mcpmetainfo.core.controller;

import com.pajk.mcpmetainfo.core.model.ServiceCollectionFilter;
import com.pajk.mcpmetainfo.core.service.ServiceCollectionFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务过滤规则管理API控制器
 * 
 * 提供过滤规则的创建、查询、更新、删除等REST API
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/filters")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class FilterController {
    
    private final ServiceCollectionFilterService filterService;
    
    /**
     * 创建过滤规则
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createFilter(@RequestBody CreateFilterRequest request) {
        try {
            ServiceCollectionFilter filter = ServiceCollectionFilter.builder()
                    .filterType(request.getFilterType() != null ? 
                            ServiceCollectionFilter.FilterType.valueOf(request.getFilterType()) : 
                            ServiceCollectionFilter.FilterType.PREFIX)
                    .filterValue(request.getFilterValue())
                    .filterOperator(request.getFilterOperator() != null ? 
                            ServiceCollectionFilter.FilterOperator.valueOf(request.getFilterOperator()) : 
                            ServiceCollectionFilter.FilterOperator.EXCLUDE)
                    .priority(request.getPriority() != null ? request.getPriority() : 0)
                    .enabled(request.getEnabled() != null ? request.getEnabled() : true)
                    .description(request.getDescription())
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
            
            ServiceCollectionFilter created = filterService.addFilter(filter);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", created.getId());
            response.put("filterType", created.getFilterType());
            response.put("filterValue", created.getFilterValue());
            response.put("filterOperator", created.getFilterOperator());
            response.put("priority", created.getPriority());
            response.put("enabled", created.getEnabled());
            response.put("message", "过滤规则创建成功");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("创建过滤规则失败: 无效的参数", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "无效的参数: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("创建过滤规则失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "创建过滤规则失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 获取所有过滤规则
     */
    @GetMapping
    public ResponseEntity<List<ServiceCollectionFilter>> getAllFilters() {
        try {
            List<ServiceCollectionFilter> filters = filterService.getAllFilters();
            return ResponseEntity.ok(filters);
        } catch (Exception e) {
            log.error("获取过滤规则列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取启用的过滤规则
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<ServiceCollectionFilter>> getEnabledFilters() {
        try {
            List<ServiceCollectionFilter> filters = filterService.getEnabledFilters();
            return ResponseEntity.ok(filters);
        } catch (Exception e) {
            log.error("获取启用的过滤规则列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取过滤规则详情
     */
    @GetMapping("/{filterId}")
    public ResponseEntity<ServiceCollectionFilter> getFilter(@PathVariable Long filterId) {
        try {
            ServiceCollectionFilter filter = filterService.getFilter(filterId);
            if (filter == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(filter);
        } catch (Exception e) {
            log.error("获取过滤规则详情失败: {}", filterId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 更新过滤规则
     */
    @PutMapping("/{filterId}")
    public ResponseEntity<Map<String, Object>> updateFilter(
            @PathVariable Long filterId,
            @RequestBody UpdateFilterRequest request) {
        try {
            ServiceCollectionFilter filter = filterService.getFilter(filterId);
            if (filter == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "过滤规则不存在");
                return ResponseEntity.status(404).body(error);
            }
            
            // 更新字段
            if (request.getFilterType() != null) {
                filter.setFilterType(ServiceCollectionFilter.FilterType.valueOf(request.getFilterType()));
            }
            if (request.getFilterValue() != null) {
                filter.setFilterValue(request.getFilterValue());
            }
            if (request.getFilterOperator() != null) {
                filter.setFilterOperator(ServiceCollectionFilter.FilterOperator.valueOf(request.getFilterOperator()));
            }
            if (request.getPriority() != null) {
                filter.setPriority(request.getPriority());
            }
            if (request.getEnabled() != null) {
                filter.setEnabled(request.getEnabled());
            }
            if (request.getDescription() != null) {
                filter.setDescription(request.getDescription());
            }
            filter.setUpdatedAt(java.time.LocalDateTime.now());
            
            filterService.updateFilter(filter);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", filter.getId());
            response.put("message", "过滤规则更新成功");
            
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("更新过滤规则失败: 无效的参数", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "无效的参数: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("更新过滤规则失败: {}", filterId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "更新过滤规则失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 启用/禁用过滤规则
     */
    @PatchMapping("/{filterId}/enable")
    public ResponseEntity<Map<String, Object>> enableFilter(
            @PathVariable Long filterId,
            @RequestParam(defaultValue = "true") boolean enabled) {
        try {
            ServiceCollectionFilter filter = filterService.getFilter(filterId);
            if (filter == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "过滤规则不存在");
                return ResponseEntity.status(404).body(error);
            }
            
            filter.setEnabled(enabled);
            filter.setUpdatedAt(java.time.LocalDateTime.now());
            filterService.updateFilter(filter);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", filterId);
            response.put("enabled", enabled);
            response.put("message", enabled ? "过滤规则已启用" : "过滤规则已禁用");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("更新过滤规则状态失败: {}", filterId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "更新过滤规则状态失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 删除过滤规则
     */
    @DeleteMapping("/{filterId}")
    public ResponseEntity<Map<String, Object>> deleteFilter(@PathVariable Long filterId) {
        try {
            boolean deleted = filterService.deleteFilter(filterId);
            if (!deleted) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "过滤规则不存在");
                return ResponseEntity.status(404).body(error);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", filterId);
            response.put("message", "过滤规则删除成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("删除过滤规则失败: {}", filterId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "删除过滤规则失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 测试过滤规则
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testFilter(@RequestBody TestFilterRequest request) {
        try {
            boolean shouldCollect = filterService.shouldCollect(
                    request.getServiceInterface(),
                    request.getServiceVersion(),
                    request.getServiceGroup());
            
            Map<String, Object> response = new HashMap<>();
            response.put("serviceInterface", request.getServiceInterface());
            response.put("serviceVersion", request.getServiceVersion());
            response.put("serviceGroup", request.getServiceGroup());
            response.put("shouldCollect", shouldCollect);
            response.put("message", shouldCollect ? "服务应该被采集" : "服务应该被过滤");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("测试过滤规则失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "测试过滤规则失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 创建过滤规则请求
     */
    public static class CreateFilterRequest {
        private String filterType; // PREFIX, SUFFIX, PATTERN, EXACT
        private String filterValue;
        private String filterOperator; // INCLUDE, EXCLUDE
        private Integer priority;
        private Boolean enabled;
        private String description;
        
        // Getters and Setters
        public String getFilterType() { return filterType; }
        public void setFilterType(String filterType) { this.filterType = filterType; }
        
        public String getFilterValue() { return filterValue; }
        public void setFilterValue(String filterValue) { this.filterValue = filterValue; }
        
        public String getFilterOperator() { return filterOperator; }
        public void setFilterOperator(String filterOperator) { this.filterOperator = filterOperator; }
        
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * 更新过滤规则请求
     */
    public static class UpdateFilterRequest {
        private String filterType;
        private String filterValue;
        private String filterOperator;
        private Integer priority;
        private Boolean enabled;
        private String description;
        
        // Getters and Setters
        public String getFilterType() { return filterType; }
        public void setFilterType(String filterType) { this.filterType = filterType; }
        
        public String getFilterValue() { return filterValue; }
        public void setFilterValue(String filterValue) { this.filterValue = filterValue; }
        
        public String getFilterOperator() { return filterOperator; }
        public void setFilterOperator(String filterOperator) { this.filterOperator = filterOperator; }
        
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
    
    /**
     * 测试过滤规则请求
     */
    public static class TestFilterRequest {
        private String serviceInterface;
        private String serviceVersion;
        private String serviceGroup;
        
        // Getters and Setters
        public String getServiceInterface() { return serviceInterface; }
        public void setServiceInterface(String serviceInterface) { this.serviceInterface = serviceInterface; }
        
        public String getServiceVersion() { return serviceVersion; }
        public void setServiceVersion(String serviceVersion) { this.serviceVersion = serviceVersion; }
        
        public String getServiceGroup() { return serviceGroup; }
        public void setServiceGroup(String serviceGroup) { this.serviceGroup = serviceGroup; }
    }
}

