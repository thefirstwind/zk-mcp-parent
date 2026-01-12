package com.pajk.mcpmetainfo.persistence.typehandler;

import com.pajk.mcpmetainfo.core.model.VirtualProjectEndpoint;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * VirtualProjectEndpoint.EndpointStatus 枚举类型处理器
 */
@MappedTypes(VirtualProjectEndpoint.EndpointStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class EndpointStatusTypeHandler extends BaseTypeHandler<VirtualProjectEndpoint.EndpointStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, VirtualProjectEndpoint.EndpointStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public VirtualProjectEndpoint.EndpointStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : VirtualProjectEndpoint.EndpointStatus.valueOf(value);
    }

    @Override
    public VirtualProjectEndpoint.EndpointStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : VirtualProjectEndpoint.EndpointStatus.valueOf(value);
    }

    @Override
    public VirtualProjectEndpoint.EndpointStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : VirtualProjectEndpoint.EndpointStatus.valueOf(value);
    }
}


