package com.pajk.mcpmetainfo.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pajk.mcpmetainfo.core.model.wizard.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 元数据补全服务
 * 目前使用启发式规则生成，未来可接入 LLM
 */
@Slf4j
@Service
public class AiMetadataEnrichmentService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 补全元数据
     */
    public EnrichedMetadata enrichMetadata(EnrichmentRequest request) {
        log.info("开始补全元数据: project={}", request.getProjectName());
        
        EnrichedMetadata result = new EnrichedMetadata();
        
        // 1. 生成项目描述
        result.setSuggestedDescription(generateDescription(request));
        
        // 2. 生成 Tool 定义
        // 2. 生成 Tool 定义
        result.setTools(generateTools(request.getSelectedInterfaces(), request.getGitMetadata()));
        
        return result;
    }

    private String generateDescription(EnrichmentRequest request) {
        // 优先使用 Git 描述
        if (request.getGitMetadata() != null && 
            request.getGitMetadata().getDescription() != null && 
            !request.getGitMetadata().getDescription().isEmpty()) {
            return request.getGitMetadata().getDescription();
        }
        
        // 其次使用原有描述
        if (request.getOriginalDescription() != null && !request.getOriginalDescription().isEmpty()) {
            return request.getOriginalDescription();
        }
        
        // 最后自动生成
        StringBuilder sb = new StringBuilder();
        sb.append("虚拟项目 ").append(request.getProjectName());
        
        if (request.getSelectedInterfaces() != null && !request.getSelectedInterfaces().isEmpty()) {
            sb.append("，包含以下服务接口：");
            String interfaces = request.getSelectedInterfaces().stream()
                    .map(DubboInterfaceInfo::getSimpleClassName)
                    .collect(Collectors.joining(", "));
            sb.append(interfaces);
            sb.append("。");
        } else {
            sb.append("。");
        }
        
        sb.append(" (由 Virtual Project Wizard 自动生成)");
        return sb.toString();
    }

    private List<McpToolDefinition> generateTools(List<DubboInterfaceInfo> interfaces, GitProjectMetadata gitMetadata) {
        List<McpToolDefinition> tools = new ArrayList<>();
        
        if (interfaces == null) return tools;
        
        for (DubboInterfaceInfo info : interfaces) {
            if (info.getMethods() == null) continue;
            
            for (MethodInfo method : info.getMethods()) {
                // 仅处理选中的方法 (假设前端传来的都是选中的，或者检查 selected 字段)
                // 这里假设传入的列表已经是过滤后的，或者我们检查 selected 字段
                if (method.isSelected()) {
                    tools.add(generateToolForMethod(info, method, gitMetadata));
                }
            }
        }
        
        return tools;
    }

    private McpToolDefinition generateToolForMethod(DubboInterfaceInfo interfaceInfo, MethodInfo method, GitProjectMetadata gitMetadata) {
        // 使用 全限定类名 + 方法名 作为 Tool 名称
        // 例如: com.pajk.provider1.service.UserService.deleteUser
        String toolName = interfaceInfo.getInterfaceName() + "." + method.getMethodName();
        
        // 尝试从 Javadoc 获取描述
        String description = null;
        if (gitMetadata != null && gitMetadata.getMethodJavadocMap() != null) {
            String key = interfaceInfo.getInterfaceName() + "#" + method.getMethodName();
            description = gitMetadata.getMethodJavadocMap().get(key);
        }
        
        // 如果没有 Javadoc，使用 NLP 启发式分析
        if (description == null || description.isEmpty()) {
            description = nlpAnalyzeMethodName(method.getMethodName());
            // 如果分析结果太简单，补充上下文
            if (!description.contains(interfaceInfo.getSimpleClassName())) {
                 description = String.format("%s (%s)", description, interfaceInfo.getSimpleClassName());
            }
        }

        // 尝试修正参数名
        if (gitMetadata != null && gitMetadata.getMethodParameterNamesMap() != null) {
             String key = interfaceInfo.getInterfaceName() + "#" + method.getMethodName();
             List<String> realNames = gitMetadata.getMethodParameterNamesMap().get(key);
             if (realNames != null && realNames.size() == method.getParameters().size()) {
                 for (int i = 0; i < realNames.size(); i++) {
                     method.getParameters().get(i).setName(realNames.get(i));
                 }
             }
        }

        // 尝试添加返回值描述
        if (gitMetadata != null && gitMetadata.getMethodReturnJavadocMap() != null) {
             String key = interfaceInfo.getInterfaceName() + "#" + method.getMethodName();
             String returnDesc = gitMetadata.getMethodReturnJavadocMap().get(key);
             if (returnDesc != null && !returnDesc.isEmpty()) {
                 description += ". 返回值: " + returnDesc;
             }
        }
        
        // 生成 JSON Schema
        String schema = generateJsonSchema(method.getParameters(), gitMetadata, interfaceInfo.getInterfaceName() + "#" + method.getMethodName());
        
        return McpToolDefinition.builder()
                .interfaceName(interfaceInfo.getInterfaceName())
                .methodName(method.getMethodName())
                .toolName(toolName)
                .description(description)
                .inputSchema(schema)
                .build();
    }

    private String generateJsonSchema(List<ParameterInfo> parameters, GitProjectMetadata gitMetadata, String javadocKeyPrefix) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = schema.putArray("required");
        
        if (parameters != null) {
            for (ParameterInfo param : parameters) {
                ObjectNode paramNode = properties.putObject(param.getName());
                paramNode.put("type", mapJavaTypeToJsonType(param.getType()));
                
                String desc = "参数: " + param.getName() + " (类型: " + param.getTypeSimpleName() + ")";
                
                // 增加更多的详细说明提示，帮助 LLM 更好理解
                if (gitMetadata != null && gitMetadata.getMethodParamJavadocMap() != null) {
                    String pKey = javadocKeyPrefix + "#" + param.getName();
                    String javadocDesc = gitMetadata.getMethodParamJavadocMap().get(pKey);
                    if (javadocDesc != null && !javadocDesc.isEmpty()) {
                        desc = javadocDesc + " (" + param.getTypeSimpleName() + ")";
                    }
                } else {
                    // 默认启发式描述
                    if (param.getName().toLowerCase().contains("id")) {
                        desc += ". 通常是唯一标识符 ID。";
                    } else if (param.getName().toLowerCase().contains("name")) {
                        desc += ". 通常是名称或标题。";
                    } else if (param.getName().toLowerCase().contains("page")) {
                        desc += ". 分页参数。";
                    }
                }
                paramNode.put("description", desc);

                
                required.add(param.getName());
            }
        }
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String mapJavaTypeToJsonType(String javaType) {
        String lower = javaType.toLowerCase();
        if (lower.contains("int") || lower.contains("long") || lower.contains("short") || lower.contains("byte")) {
            return "integer";
        } else if (lower.contains("double") || lower.contains("float") || lower.contains("number") || lower.contains("decimal")) {
            return "number";
        } else if (lower.contains("boolean")) {
            return "boolean";
        } else if (lower.contains("list") || lower.contains("array") || lower.endsWith("[]") || lower.contains("set") || lower.contains("collection")) {
            return "array";
        } else if (lower.contains("map") || lower.contains("object") || lower.contains("json") || lower.contains("dto") || lower.contains("request") || lower.contains("vo")) {
            return "object";
        }
        return "string";

    }

    private String toSnakeCase(String camelCase) {
        Pattern pattern = Pattern.compile("([a-z])([A-Z]+)");
        Matcher matcher = pattern.matcher(camelCase);
        return matcher.replaceAll("$1_$2").toLowerCase();
    }

    private String nlpAnalyzeMethodName(String methodName) {
        // 简单启发式: 拆分驼峰并映射动词
        // e.g. deleteUser -> 删除 User
        String[] words = methodName.split("(?<!^)(?=[A-Z])");
        if (words.length == 0) return methodName;
        
        String verb = words[0].toLowerCase();
        String object = "";
        if (words.length > 1) {
            StringBuilder sb = new StringBuilder();
            for (int i=1; i<words.length; i++) {
                sb.append(words[i]).append(" ");
            }
            object = sb.toString().trim();
        }
        
        String action = verb;
        switch (verb) {
            case "get":
            case "query":
            case "find":
            case "list":
            case "search":
            case "fetch":
                action = "查询";
                break;
            case "create":
            case "add":
            case "save":
            case "insert":
                action = "创建";
                break;
            case "update":
            case "modify":
            case "edit":
                action = "更新";
                break;
            case "delete":
            case "remove":
                action = "删除";
                break;
            case "check":
            case "validate":
                action = "校验";
                break;
            case "process":
            case "handle":
                action = "处理";
                break;
            default:
                // 如果不认识动词，保留英文但首字母大写
                if (verb.length() > 0) {
                    action = verb.substring(0, 1).toUpperCase() + verb.substring(1);
                }
        }
        
        if (object.isEmpty()) return action;
        return action + " " + object;
    }
}
