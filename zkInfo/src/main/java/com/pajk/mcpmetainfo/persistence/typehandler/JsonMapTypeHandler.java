package com.pajk.mcpmetainfo.persistence.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * JSON Map TypeHandler
 * 
 * 用于处理 Map<String, String> 类型的字段，将其序列化为 JSON 字符串存储到数据库，
 * 从数据库读取时反序列化为 Map 对象。
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-17
 */
@MappedTypes({Map.class})
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonMapTypeHandler extends BaseTypeHandler<Map<String, String>> {

    private static final Logger log = LoggerFactory.getLogger(JsonMapTypeHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, String> parameter, JdbcType jdbcType) throws SQLException {
        try {
            if (parameter == null || parameter.isEmpty()) {
                ps.setString(i, null);
            } else {
                String json = objectMapper.writeValueAsString(parameter);
                ps.setString(i, json);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Map to JSON: {}", e.getMessage(), e);
            throw new SQLException("Failed to serialize JSON", e);
        }
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return parseJson(json);
    }

    @Override
    public Map<String, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String json = rs.getString(columnIndex);
        return parseJson(json);
    }

    @Override
    public Map<String, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String json = cs.getString(columnIndex);
        return parseJson(json);
    }

    private Map<String, String> parseJson(String json) throws SQLException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, 
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize JSON to Map: {}, returning null", e.getMessage());
            return null;
        }
    }
}


