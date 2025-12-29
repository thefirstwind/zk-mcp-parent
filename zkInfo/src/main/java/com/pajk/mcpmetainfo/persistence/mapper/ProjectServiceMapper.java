package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProjectServiceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectServiceMapper {

    void insert(ProjectServiceEntity entity);

    void update(ProjectServiceEntity entity);

    ProjectServiceEntity findById(@Param("id") Long id);

    List<ProjectServiceEntity> findByProjectId(@Param("projectId") Long projectId);

    List<ProjectServiceEntity> findAll();

    void deleteById(@Param("id") Long id);

    void deleteByProjectId(@Param("projectId") Long projectId);
}


