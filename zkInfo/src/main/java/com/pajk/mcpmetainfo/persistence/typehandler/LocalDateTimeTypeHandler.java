package com.pajk.mcpmetainfo.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.LocalDateTime;

/**
 * LocalDateTime TypeHandler for TDDL compatibility
 * 
 * TDDL 的 TResultSetWrapper 不支持 ResultSet.getObject(String, Class) 方法，
 * 因此使用兼容的方式：通过 getTimestamp() 获取时间戳，然后转换为 LocalDateTime
 * 
 * @author ZkInfo Team
 * @version 1.0.0
 * @since 2025-12-30
 */
@MappedTypes(LocalDateTime.class)
@MappedJdbcTypes({JdbcType.TIMESTAMP, JdbcType.DATE, JdbcType.TIME})
public class LocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType) throws SQLException {
        if (parameter == null) {
            ps.setTimestamp(i, null);
        } else {
            ps.setTimestamp(i, Timestamp.valueOf(parameter));
        }
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        // 使用 getTimestamp() 而不是 getObject(String, Class)，以兼容 TDDL
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        // 使用 getTimestamp() 而不是 getObject(int, Class)，以兼容 TDDL
        Timestamp timestamp = rs.getTimestamp(columnIndex);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        // 使用 getTimestamp() 而不是 getObject(int, Class)，以兼容 TDDL
        Timestamp timestamp = cs.getTimestamp(columnIndex);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}


