package com.pajk.mcpmetainfo.core.util;

import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

/**
 * ZooKeeper路径解析工具类
 * 
 * 用于将 zk_path 解析为结构化字段，便于数据库存储和查询
 * 
 * zk_path 格式示例：
 * - /dubbo/com.example.Service/providers/dubbo://192.168.1.100:20880/com.example.Service?version=1.0.0&group=default
 * - /dubbo/com.example.Service/consumers/...
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Slf4j
public class ZkPathParser {
    
    /**
     * 解析结果
     */
    public static class ParseResult {
        private String pathRoot;          // 路径根（如：/dubbo）
        private String pathInterface;     // 路径中的接口名
        private String pathAddress;       // 路径中的地址（IP:Port）
        private String pathProtocol;       // 路径中的协议
        private String pathVersion;        // 路径中的版本
        private String pathGroup;          // 路径中的分组
        private String pathApplication;     // 路径中的应用名
        
        // Getters and Setters
        public String getPathRoot() { return pathRoot; }
        public void setPathRoot(String pathRoot) { this.pathRoot = pathRoot; }
        
        public String getPathInterface() { return pathInterface; }
        public void setPathInterface(String pathInterface) { this.pathInterface = pathInterface; }
        
        public String getPathAddress() { return pathAddress; }
        public void setPathAddress(String pathAddress) { this.pathAddress = pathAddress; }
        
        public String getPathProtocol() { return pathProtocol; }
        public void setPathProtocol(String pathProtocol) { this.pathProtocol = pathProtocol; }
        
        public String getPathVersion() { return pathVersion; }
        public void setPathVersion(String pathVersion) { this.pathVersion = pathVersion; }
        
        public String getPathGroup() { return pathGroup; }
        public void setPathGroup(String pathGroup) { this.pathGroup = pathGroup; }
        
        public String getPathApplication() { return pathApplication; }
        public void setPathApplication(String pathApplication) { this.pathApplication = pathApplication; }
    }
    
    /**
     * 解析 zk_path 为结构化字段
     * 
     * @param zkPath ZooKeeper路径
     * @return 解析结果，如果解析失败返回 null
     */
    public static ParseResult parse(String zkPath) {
        if (zkPath == null || zkPath.isEmpty()) {
            return null;
        }
        
        try {
            ParseResult result = new ParseResult();
            
            // 移除末尾的斜杠
            String path = zkPath.trim();
            if (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            
            // 按 / 分割路径
            String[] parts = path.split("/");
            
            if (parts.length < 2) {
                log.warn("zk_path 格式不正确，至少需要2个部分: {}", zkPath);
                return null;
            }
            
            // parts[0] 是空字符串（因为路径以 / 开头）
            // parts[1] 通常是路径根（如：dubbo）
            if (parts.length > 1) {
                result.setPathRoot("/" + parts[1]);
            }
            
            // 查找 providers 或 consumers 节点
            int interfaceIndex = -1;
            for (int i = 2; i < parts.length; i++) {
                if ("providers".equals(parts[i]) || "consumers".equals(parts[i])) {
                    // 下一个部分应该是接口名
                    if (i + 1 < parts.length) {
                        interfaceIndex = i + 1;
                        result.setPathInterface(parts[interfaceIndex]);
                    }
                    break;
                } else if (i == 2) {
                    // 如果没有 providers/consumers，第二个部分可能就是接口名
                    interfaceIndex = i;
                    result.setPathInterface(parts[interfaceIndex]);
                }
            }
            
            // 如果找到了接口名，继续解析后续部分
            if (interfaceIndex > 0 && interfaceIndex + 1 < parts.length) {
                // 尝试解析 URL 格式的地址
                String urlPart = parts[interfaceIndex + 1];
                parseUrlPart(urlPart, result);
            } else if (parts.length > 2) {
                // 尝试从第二个部分开始解析（可能是简化的路径格式）
                // 格式: /interfaceName/address:port/protocol/version/...
                if (parts.length >= 3) {
                    result.setPathInterface(parts[1]);
                    result.setPathAddress(parts[2]);
                }
                if (parts.length >= 4) {
                    result.setPathProtocol(parts[3]);
                }
                if (parts.length >= 5) {
                    result.setPathVersion(parts[4]);
                }
                if (parts.length >= 6) {
                    result.setPathGroup(parts[5]);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            log.warn("解析 zk_path 失败: {}", zkPath, e);
            return null;
        }
    }
    
    /**
     * 解析 URL 部分
     * 格式: dubbo://192.168.1.100:20880/com.example.Service?version=1.0.0&group=default&application=app
     */
    private static void parseUrlPart(String urlPart, ParseResult result) {
        try {
            // 分离 URL 和参数
            String[] urlAndParams = urlPart.split("\\?");
            String baseUrl = urlAndParams[0];
            
            // 解析协议和地址
            if (baseUrl.contains("://")) {
                String[] protocolAndRest = baseUrl.split("://", 2);
                result.setPathProtocol(protocolAndRest[0]);
                
                String rest = protocolAndRest[1];
                // 提取地址（IP:Port）
                if (rest.contains("/")) {
                    String[] addressAndInterface = rest.split("/", 2);
                    result.setPathAddress(addressAndInterface[0]);
                    if (addressAndInterface.length > 1) {
                        result.setPathInterface(addressAndInterface[1]);
                    }
                } else {
                    result.setPathAddress(rest);
                }
            }
            
            // 解析参数
            if (urlAndParams.length > 1) {
                Map<String, String> params = parseParams(urlAndParams[1]);
                result.setPathVersion(params.get("version"));
                result.setPathGroup(params.get("group"));
                result.setPathApplication(params.get("application"));
            }
            
        } catch (Exception e) {
            log.warn("解析 URL 部分失败: {}", urlPart, e);
        }
    }
    
    /**
     * 解析参数字符串
     * 格式: version=1.0.0&group=default&application=app
     */
    private static Map<String, String> parseParams(String paramString) {
        Map<String, String> params = new HashMap<>();
        try {
            String[] pairs = paramString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        } catch (Exception e) {
            log.warn("解析参数字符串失败: {}", paramString, e);
        }
        return params;
    }
    
    /**
     * 填充 ProviderInfoEntity 的结构化路径字段
     * 
     * 注意：此方法已废弃，因为 zk_provider_info 表已移除 path_* 字段
     * 路径信息现在通过 service_id 和 node_id 关联 zk_dubbo_service 和 zk_dubbo_service_node 获取
     * 
     * @param entity ProviderInfoEntity 对象
     * @param zkPath ZooKeeper路径
     * @deprecated 路径字段已移除，不再需要填充
     */
    @Deprecated
    public static void fillPathFields(com.pajk.mcpmetainfo.persistence.entity.ProviderInfoEntity entity, String zkPath) {
        // 路径字段已移除，此方法不再执行任何操作
        // 保留方法签名以避免编译错误，但实际不执行任何操作
    }
    
    /**
     * 截断字符串到指定长度
     * 
     * @param value 原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串，如果超过长度则截断并记录警告
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        // 截断并记录警告
        String truncated = value.substring(0, maxLength);
        log.warn("字段值过长，已截断: 原始长度={}, 最大长度={}, 截断后={}...", 
                value.length(), maxLength, truncated);
        return truncated;
    }
}

