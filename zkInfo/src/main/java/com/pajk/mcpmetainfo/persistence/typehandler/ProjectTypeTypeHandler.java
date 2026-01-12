package com.pajk.mcpmetainfo.persistence.typehandler;

import com.pajk.mcpmetainfo.core.model.Project;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Project.ProjectType 枚举类型处理器
 */
@MappedTypes(Project.ProjectType.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class ProjectTypeTypeHandler extends BaseTypeHandler<Project.ProjectType> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Project.ProjectType parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public Project.ProjectType getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : Project.ProjectType.valueOf(value);
    }

    @Override
    public Project.ProjectType getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : Project.ProjectType.valueOf(value);
    }

    @Override
    public Project.ProjectType getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : Project.ProjectType.valueOf(value);
    }
}


