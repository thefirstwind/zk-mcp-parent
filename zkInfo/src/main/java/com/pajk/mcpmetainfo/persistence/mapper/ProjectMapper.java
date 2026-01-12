package com.pajk.mcpmetainfo.persistence.mapper;

import com.pajk.mcpmetainfo.persistence.entity.ProjectEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProjectMapper {
    
    void insert(ProjectEntity project);
    
    void update(ProjectEntity project);
    
    void deleteById(@Param("id") Long id);
    
    ProjectEntity findById(@Param("id") Long id);
    
    ProjectEntity findByProjectCode(@Param("projectCode") String projectCode);
    
    List<ProjectEntity> findAll();
    
    List<ProjectEntity> findByProjectType(@Param("projectType") String projectType);
    
    List<ProjectEntity> findByStatus(@Param("status") String status);
}



