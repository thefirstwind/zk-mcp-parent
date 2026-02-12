package com.pajk.mcpmetainfo.core.controller;

import com.pajk.mcpmetainfo.core.service.VirtualProjectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 虚拟项目管理API控制器
 * 
 * 提供虚拟项目的创建、查询、更新、删除等REST API
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-01-15
 */
@Slf4j
@RestController
@RequestMapping("/api/virtual-projects")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class VirtualProjectController {
    
    private final VirtualProjectService virtualProjectService;
    private final com.pajk.mcpmetainfo.core.service.VirtualProjectRegistrationService registrationService;
    
    /**
     * 创建虚拟项目
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createVirtualProject(
            @RequestBody VirtualProjectService.CreateVirtualProjectRequest request) {
        try {
            VirtualProjectService.VirtualProjectInfo virtualProject = 
                    virtualProjectService.createVirtualProject(request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("project", virtualProject.getProject());
            response.put("endpoint", virtualProject.getEndpoint());
            response.put("serviceCount", virtualProject.getServiceCount());
            response.put("message", "虚拟项目创建成功");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("创建虚拟项目失败", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "创建虚拟项目失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    /**
     * 获取所有虚拟项目
     */
    @GetMapping
    public ResponseEntity<List<VirtualProjectService.VirtualProjectInfo>> getAllVirtualProjects() {
        try {
            List<VirtualProjectService.VirtualProjectInfo> projects = 
                    virtualProjectService.getAllVirtualProjects();
            return ResponseEntity.ok(projects);
        } catch (Exception e) {
            log.error("获取虚拟项目列表失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取虚拟项目详情 (通过 endpointName)
     */
    @GetMapping("/{endpointName}")
    public ResponseEntity<VirtualProjectService.VirtualProjectInfo> getVirtualProject(
            @PathVariable String endpointName) {
        try {
            VirtualProjectService.VirtualProjectInfo project = findProjectByEndpointName(endpointName);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(project);
        } catch (Exception e) {
            log.error("获取虚拟项目详情失败: {}", endpointName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除虚拟项目 (通过 endpointName)
     */
    @DeleteMapping("/{endpointName}")
    public ResponseEntity<Map<String, Object>> deleteVirtualProject(
            @PathVariable String endpointName) {
        try {
            boolean success = virtualProjectService.deleteVirtualProjectByServiceName(endpointName);
            
            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("message", "虚拟项目删除成功");
                response.put("endpointName", endpointName);
                response.put("deletedFromNacos", true);
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "虚拟项目删除失败");
                response.put("endpointName", endpointName);
                return ResponseEntity.internalServerError().body(response);
            }
        } catch (Exception e) {
            log.error("删除虚拟项目失败: endpointName={}", endpointName, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "删除虚拟项目失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 获取虚拟项目的服务列表 (通过 endpointName)
     */
    @GetMapping("/{endpointName}/services")
    public ResponseEntity<List<com.pajk.mcpmetainfo.core.model.ProjectService>> getVirtualProjectServices(
            @PathVariable String endpointName) {
        try {
            VirtualProjectService.VirtualProjectInfo project = findProjectByEndpointName(endpointName);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(project.getServices());
        } catch (Exception e) {
            log.error("获取虚拟项目服务列表失败: endpointName={}", endpointName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 更新虚拟项目的服务列表 (通过 endpointName)
     */
    @PutMapping("/{endpointName}/services")
    public ResponseEntity<Map<String, Object>> updateVirtualProjectServices(
            @PathVariable String endpointName,
            @RequestBody UpdateServicesRequest request) {
        try {
            VirtualProjectService.VirtualProjectInfo project = findProjectByEndpointName(endpointName);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }

            virtualProjectService.updateVirtualProjectServices(
                    project.getProject().getId(), request.getServices());

            Map<String, Object> response = new HashMap<>();
            response.put("message", "虚拟项目服务列表更新成功");
            response.put("endpointName", endpointName);
            response.put("serviceCount", request.getServices().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("更新虚拟项目服务列表失败: endpointName={}", endpointName, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "更新虚拟项目服务列表失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 获取虚拟项目的工具列表 (通过 endpointName)
     */
    @GetMapping("/{endpointName}/tools")
    public ResponseEntity<Map<String, Object>> getVirtualProjectTools(
            @PathVariable String endpointName) {
        try {
            List<Map<String, Object>> tools = registrationService.getVirtualProjectToolsByEndpointName(endpointName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("endpointName", endpointName);
            response.put("tools", tools);
            response.put("toolCount", tools.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("获取虚拟项目工具列表失败: {}", endpointName, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "获取虚拟项目工具列表失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 重新注册虚拟项目到 Nacos (通过 endpointName)
     */
    @PostMapping("/{endpointName}/reregister")
    public ResponseEntity<Map<String, Object>> reregisterVirtualProject(
            @PathVariable String endpointName) {
        try {
            VirtualProjectService.VirtualProjectInfo project = findProjectByEndpointName(endpointName);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }
            
            // 重新注册到 Nacos
            registrationService.reregisterVirtualProjectToNacos(
                    project.getProject(), 
                    project.getEndpoint()
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "虚拟项目重新注册成功");
            response.put("endpointName", endpointName);
            response.put("mcpServiceName", "virtual-" + endpointName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("重新注册虚拟项目失败: {}", endpointName, e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "重新注册虚拟项目失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    // 助手方法：根据 endpointName 查找项目
    private VirtualProjectService.VirtualProjectInfo findProjectByEndpointName(String endpointName) {
        return virtualProjectService.getVirtualProjectByEndpointName(endpointName);
    }

    /**
     * 更新服务列表请求
     */
    public static class UpdateServicesRequest {
        private List<VirtualProjectService.ServiceSelection> services;
        
        public List<VirtualProjectService.ServiceSelection> getServices() { return services; }
        public void setServices(List<VirtualProjectService.ServiceSelection> services) { 
            this.services = services; 
        }
    }
}

