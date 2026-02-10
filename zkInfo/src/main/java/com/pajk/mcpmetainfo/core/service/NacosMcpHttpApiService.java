package com.pajk.mcpmetainfo.core.service;

import com.alibaba.nacos.api.ai.model.mcp.*;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacos MCP HTTP API 服务
 * 直接调用 Nacos v3 控制台 API 注册 MCP 服务
 */
@Slf4j
@Service
public class NacosMcpHttpApiService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${nacos.server-addr:127.0.0.1:8848}")
    private String nacosServerAddr;
    
    @Value("${nacos.context.path:/nacos}")
    private String contextPath;
    
    @Value("${nacos.namespace:public}")
    private String nacosNamespace;
    
    @Value("${nacos.username:}")
    private String nacosUsername;
    
    @Value("${nacos.password:}")
    private String nacosPassword;
    
    private String accessToken;
    private long tokenExpirationTime;
    
    /**
     * 获取 Nacos AccessToken
     */
    private synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpirationTime) {
            return accessToken;
        }
        
        try {
            String url = "http://" + nacosServerAddr + contextPath + "/v1/auth/login";
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("username", nacosUsername);
            params.add("password", nacosPassword);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                accessToken = (String) body.get("accessToken");
                Integer tokenTtl = (Integer) body.get("tokenTtl");
                tokenExpirationTime = System.currentTimeMillis() + (tokenTtl - 60) * 1000L;
                log.info("✅ Successfully obtained Nacos access token");
                return accessToken;
            }
        } catch (Exception e) {
            log.error("❌ Failed to get Nacos access token: {}", e.getMessage(), e);
        }
        return null;
    }
    
    /**
     * 创建 MCP 服务
     * 
     * @param mcpName MCP服务名称
     * @param serverBasicInfo 服务器基本信息
     * @param toolSpec 工具规格
     * @param endpointSpec 端点规格
     * @return 是否成功
     */
    public boolean createMcpServer(String mcpName, McpServerBasicInfo serverBasicInfo, 
                                   McpToolSpecification toolSpec, McpEndpointSpec endpointSpec) {
        try {
            String url = "http://" + nacosServerAddr + contextPath + "/v3/admin/ai/mcp";
            
            // 构建 JSON 请求体
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("namespaceId", nacosNamespace);
            params.add("mcpName", mcpName);
            
            // 将对象转换为 JSON 字符串作为表单项
            params.add("serverSpecification", objectMapper.writeValueAsString(serverBasicInfo));
            if (toolSpec != null) {
                params.add("toolSpecification", objectMapper.writeValueAsString(toolSpec));
            }
            if (endpointSpec != null) {
                params.add("endpointSpecification", objectMapper.writeValueAsString(endpointSpec));
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            String token = getAccessToken();
            if (token != null) {
                headers.set("accessToken", token);
            }
            
            log.info("🚀 Creating MCP server via HTTP API (Form): {}", mcpName);
            log.debug("Request URL: {}", url);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");
                if (code != null && code == 0) {
                    log.info("✅ Successfully created MCP server via HTTP API: {}", mcpName);
                    return true;
                } else {
                    log.error("⚠️ Failed to create MCP server. Response Body: {}", body);
                    return false;
                }
            } else {
                log.error("⚠️ Unexpected HTTP status: {}. Response Body: {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("❌ HTTP Error creating MCP server: {}. Response Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("❌ Failed to create MCP server via HTTP API: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 删除 MCP 服务
     */
    public boolean deleteMcpServer(String mcpName) {
        try {
            String url = "http://" + nacosServerAddr + contextPath + "/v3/admin/ai/mcp"
                    + "?namespaceId=" + nacosNamespace
                    + "&mcpName=" + mcpName;
            
            HttpHeaders headers = new HttpHeaders();
            String token = getAccessToken();
            if (token != null) {
                headers.set("accessToken", token);
            }
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Successfully deleted MCP server: {}", mcpName);
                return true;
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to delete MCP server: {}", e.getMessage());
        }
        return false;
    }
}
