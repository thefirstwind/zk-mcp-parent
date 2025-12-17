package com.zkinfo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Nacos v3 HTTP API 服务
 * 使用 Nacos v3 版本的客户端 API 进行服务注册、发现和配置管理
 * 参考文档: https://nacos.io/docs/latest/manual/user/open-api/
 */
@Slf4j
@Service
public class NacosV3ApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String serverAddr;

    @Value("${nacos.namespace:public}")
    private String namespace;

    @Value("${nacos.username:nacos}")
    private String username;

    @Value("${nacos.password:nacos}")
    private String password;

    @Value("${nacos.server.context-path:/nacos}")
    private String contextPath;

    public NacosV3ApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建基础 URL
     */
    private String getBaseUrl() {
        return "http://" + serverAddr + contextPath;
    }

    /**
     * 构建请求头
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("User-Agent", "Nacos-Java-Client-zkInfo");
        headers.set("Client-Version", "zkInfo-1.0.0");
        return headers;
    }

    /**
     * 注册服务实例
     * POST /nacos/v3/client/ns/instance
     * 
     * @param serviceName 服务名
     * @param ip IP地址
     * @param port 端口号
     * @param groupName 分组名，默认为DEFAULT_GROUP
     * @param clusterName 集群名称，默认为DEFAULT
     * @param ephemeral 是否为临时实例
     * @param metadata 元数据
     * @return 是否注册成功
     */
    public boolean registerInstance(String serviceName, String ip, int port, 
                                   String groupName, String clusterName, 
                                   boolean ephemeral, Map<String, String> metadata) {
        try {
            String url = getBaseUrl() + "/v3/client/ns/instance";
            
            // 构建请求参数
            Map<String, String> params = new LinkedHashMap<>();
            params.put("namespaceId", namespace);
            params.put("groupName", groupName != null ? groupName : "DEFAULT_GROUP");
            params.put("serviceName", serviceName);
            params.put("ip", ip);
            params.put("port", String.valueOf(port));
            if (clusterName != null) {
                params.put("clusterName", clusterName);
            }
            params.put("ephemeral", String.valueOf(ephemeral));
            
            // 注意：根据 Nacos v3 API 文档，metadata 应该作为请求体的一部分
            // 但文档中注册实例的接口使用的是表单格式，metadata 可能需要特殊处理
            // 这里先尝试将 metadata 作为 JSON 字符串放在请求体中
            String metadataJson = null;
            if (metadata != null && !metadata.isEmpty()) {
                try {
                    metadataJson = objectMapper.writeValueAsString(metadata);
                } catch (Exception e) {
                    log.warn("Failed to serialize metadata to JSON", e);
                }
            }
            
            // 构建请求体（表单格式）
            StringBuilder body = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (body.length() > 0) {
                    body.append("&");
                }
                body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
            
            // 如果 metadata 存在，尝试添加到请求体
            // 注意：Nacos v3 API 的 metadata 传递方式可能需要根据实际 API 文档调整
            // 根据文档，metadata 可能需要作为单独的字段传递
            if (metadataJson != null) {
                if (body.length() > 0) {
                    body.append("&");
                }
                body.append("metadata=").append(URLEncoder.encode(metadataJson, StandardCharsets.UTF_8));
            }
            
            HttpHeaders headers = buildHeaders();
            HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> bodyMap = response.getBody();
                Integer code = (Integer) bodyMap.get("code");
                if (code != null && code == 0) {
                    log.info("✅ Successfully registered instance to Nacos v3: {}:{} (service: {})", 
                            ip, port, serviceName);
                    return true;
                } else {
                    String message = (String) bodyMap.get("message");
                    log.error("❌ Failed to register instance to Nacos v3: {} (code: {})", 
                            message, code);
                    return false;
                }
            } else {
                log.error("❌ Failed to register instance to Nacos v3: HTTP {}", 
                        response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Error registering instance to Nacos v3: {}:{}", ip, port, e);
            return false;
        }
    }

    /**
     * 注销服务实例
     * DELETE /nacos/v3/client/ns/instance
     * 
     * @param serviceName 服务名
     * @param ip IP地址
     * @param port 端口号
     * @param groupName 分组名，默认为DEFAULT_GROUP
     * @return 是否注销成功
     */
    public boolean deregisterInstance(String serviceName, String ip, int port, String groupName) {
        try {
            String url = getBaseUrl() + "/v3/client/ns/instance";
            
            // 构建查询参数
            StringBuilder queryParams = new StringBuilder();
            queryParams.append("namespaceId=").append(URLEncoder.encode(namespace, StandardCharsets.UTF_8));
            queryParams.append("&groupName=").append(URLEncoder.encode(
                    groupName != null ? groupName : "DEFAULT_GROUP", StandardCharsets.UTF_8));
            queryParams.append("&serviceName=").append(URLEncoder.encode(serviceName, StandardCharsets.UTF_8));
            queryParams.append("&ip=").append(URLEncoder.encode(ip, StandardCharsets.UTF_8));
            queryParams.append("&port=").append(port);
            
            url += "?" + queryParams.toString();
            
            HttpHeaders headers = buildHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.DELETE, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> bodyMap = response.getBody();
                Integer code = (Integer) bodyMap.get("code");
                if (code != null && code == 0) {
                    String data = (String) bodyMap.get("data");
                    if ("ok".equals(data)) {
                        log.info("✅ Successfully deregistered instance from Nacos v3: {}:{} (service: {})", 
                                ip, port, serviceName);
                        return true;
                    }
                }
                String message = (String) bodyMap.get("message");
                log.error("❌ Failed to deregister instance from Nacos v3: {} (code: {})", 
                        message, code);
            } else {
                log.error("❌ Failed to deregister instance from Nacos v3: HTTP {}", 
                        response.getStatusCode());
            }
            return false;
        } catch (Exception e) {
            log.error("❌ Error deregistering instance from Nacos v3: {}:{}", ip, port, e);
            return false;
        }
    }

    /**
     * 查询指定服务的实例列表
     * GET /nacos/v3/client/ns/instance/list
     * 
     * @param serviceName 服务名
     * @param groupName 分组名，默认为DEFAULT_GROUP
     * @param clusterName 集群名称，默认为DEFAULT
     * @param healthyOnly 是否只获取健康实例
     * @return 实例列表
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getInstanceList(String serviceName, String groupName, 
                                                     String clusterName, boolean healthyOnly) {
        try {
            String url = getBaseUrl() + "/v3/client/ns/instance/list";
            
            // 构建查询参数
            StringBuilder queryParams = new StringBuilder();
            queryParams.append("namespaceId=").append(URLEncoder.encode(namespace, StandardCharsets.UTF_8));
            queryParams.append("&groupName=").append(URLEncoder.encode(
                    groupName != null ? groupName : "DEFAULT_GROUP", StandardCharsets.UTF_8));
            queryParams.append("&serviceName=").append(URLEncoder.encode(serviceName, StandardCharsets.UTF_8));
            if (clusterName != null) {
                queryParams.append("&clusterName=").append(URLEncoder.encode(clusterName, StandardCharsets.UTF_8));
            }
            queryParams.append("&healthyOnly=").append(healthyOnly);
            
            url += "?" + queryParams.toString();
            
            HttpHeaders headers = buildHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> bodyMap = response.getBody();
                Integer code = (Integer) bodyMap.get("code");
                if (code != null && code == 0) {
                    Object dataObj = bodyMap.get("data");
                    if (dataObj instanceof List) {
                        List<Map<String, Object>> instances = (List<Map<String, Object>>) dataObj;
                        log.debug("✅ Retrieved {} instances from Nacos v3 for service: {}", 
                                instances.size(), serviceName);
                        return instances;
                    }
                } else {
                    String message = (String) bodyMap.get("message");
                    log.error("❌ Failed to get instance list from Nacos v3: {} (code: {})", 
                            message, code);
                }
            } else {
                log.error("❌ Failed to get instance list from Nacos v3: HTTP {}", 
                        response.getStatusCode());
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("❌ Error getting instance list from Nacos v3: {}", serviceName, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取配置
     * GET /nacos/v3/client/cs/config
     * 
     * @param dataId 配置ID
     * @param group 配置组
     * @return 配置内容
     */
    public String getConfig(String dataId, String group) {
        try {
            String url = getBaseUrl() + "/v3/client/cs/config";
            
            // 构建查询参数
            // 注意：根据错误信息，Nacos v3 API 需要 groupName 参数，而不是 group
            StringBuilder queryParams = new StringBuilder();
            queryParams.append("namespaceId=").append(URLEncoder.encode(namespace, StandardCharsets.UTF_8));
            queryParams.append("&groupName=").append(URLEncoder.encode(
                    group != null ? group : "DEFAULT_GROUP", StandardCharsets.UTF_8));
            queryParams.append("&dataId=").append(URLEncoder.encode(dataId, StandardCharsets.UTF_8));
            
            url += "?" + queryParams.toString();
            
            HttpHeaders headers = buildHeaders();
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, Map.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> bodyMap = response.getBody();
                Integer code = (Integer) bodyMap.get("code");
                if (code != null && code == 0) {
                    Object dataObj = bodyMap.get("data");
                    String data;
                    if (dataObj == null) {
                        data = null;
                    } else if (dataObj instanceof String) {
                        data = (String) dataObj;
                    } else {
                        // 如果 data 是对象（如 Map），转换为 JSON 字符串
                        try {
                            data = objectMapper.writeValueAsString(dataObj);
                        } catch (Exception e) {
                            log.warn("⚠️ Failed to convert data object to JSON string, using toString(): {}", e.getMessage());
                            data = dataObj.toString();
                        }
                    }
                    log.debug("✅ Retrieved config from Nacos v3: {} (group: {})", dataId, group);
                    return data;
                } else {
                    String message = (String) bodyMap.get("message");
                    log.error("❌ Failed to get config from Nacos v3: {} (code: {})", 
                            message, code);
                }
            } else {
                log.error("❌ Failed to get config from Nacos v3: HTTP {}", 
                        response.getStatusCode());
            }
            return null;
        } catch (Exception e) {
            log.error("❌ Error getting config from Nacos v3: {} (group: {})", dataId, group, e);
            return null;
        }
    }
    
    /**
     * 发布配置
     * 注意：Nacos v3 客户端 API 不提供配置发布接口，需要使用 Admin API 或 SDK
     * 这里保留方法签名，但实际实现需要使用 ConfigService
     * 
     * @param dataId 配置ID
     * @param group 配置组
     * @param content 配置内容
     * @return 是否发布成功
     */
    public boolean publishConfig(String dataId, String group, String content) {
        // Nacos v3 客户端 API 不提供配置发布接口
        // 需要使用 Admin API 或保留使用 SDK
        log.warn("⚠️ Nacos v3 client API does not support publishing config, " +
                "please use Admin API or ConfigService SDK");
        return false;
    }
}

