package com.pajk.mcpmetainfo.core.model.wizard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 方法信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodInfo {
    
    /**
     * 方法名
     */
    @JsonProperty("methodName")
    private String methodName;
    
    /**
     * 返回类型
     */
    @JsonProperty("returnType")
    private String returnType;
    
    /**
     * 返回类型简单名
     */
    @JsonProperty("returnTypeSimpleName")
    private String returnTypeSimpleName;
    
    /**
     * 参数列表
     */
    @JsonProperty("parameters")
    private List<ParameterInfo> parameters;
    
    /**
     * 方法描述
     */
    @JsonProperty("description")
    private String description;
    
    /**
     * 返回值描述
     */
    @JsonProperty("returnDescription")
    private String returnDescription;
    
    /**
     * 是否被选中
     */
    @JsonProperty("selected")
    private boolean selected;
    
    /**
     * 方法签名
     */
    public String getSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(returnTypeSimpleName != null ? returnTypeSimpleName : returnType)
          .append(" ")
          .append(methodName)
          .append("(");
        
        if (parameters != null && !parameters.isEmpty()) {
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) sb.append(", ");
                ParameterInfo param = parameters.get(i);
                sb.append(param.typeSimpleName != null ? param.typeSimpleName : param.type)
                  .append(" ")
                  .append(param.name);
            }
        }
        
        sb.append(")");
        return sb.toString();
    }
}
