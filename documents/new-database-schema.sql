-- MySQL数据库表结构设计
-- 用于存储从ZooKeeper拉取的Dubbo接口数据以及审批流程信息
-- 按照新的需求，将记录表按服务维度保存，节点信息保存在单独的表中并与服务表关联

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS zk_mcp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE zk_mcp;

-- Dubbo服务信息表（服务维度）
-- 存储服务的基本信息，按服务维度保存
CREATE TABLE IF NOT EXISTS dubbo_services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    
    -- 服务基本信息
    interface_name VARCHAR(255) NOT NULL COMMENT '服务接口名',
    protocol VARCHAR(50) COMMENT '协议类型',
    version VARCHAR(50) COMMENT '服务版本',
    `group` VARCHAR(100) COMMENT '服务分组',
    application VARCHAR(100) COMMENT '应用名称',
    
    -- 审批流程字段
    approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING' COMMENT '审批状态: PENDING-待审批, APPROVED-已审批, REJECTED-已拒绝',
    approver VARCHAR(100) COMMENT '审批人',
    approval_time DATETIME COMMENT '审批时间',
    approval_comment TEXT COMMENT '审批意见',
    
    -- 统计信息
    provider_count INT DEFAULT 0 COMMENT '该服务下的Provider数量',
    online_provider_count INT DEFAULT 0 COMMENT '该服务下在线的Provider数量',
    
    -- 时间信息
    gmt_created DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 索引优化
    INDEX idx_interface_name (interface_name),
    INDEX idx_application (application),
    INDEX idx_approval_status (approval_status),
    UNIQUE KEY uk_service (interface_name, protocol, version, `group`, application)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务信息表';

-- Dubbo服务节点信息表（节点维度）
-- 存储服务节点的详细信息，与服务表关联
-- 只保存在线节点信息，每次监听到zk变更都需更新nodes表
CREATE TABLE IF NOT EXISTS dubbo_service_nodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    
    -- 关联信息
    service_id BIGINT NOT NULL COMMENT '关联的服务ID',
    
    -- 节点基本信息
    address VARCHAR(100) NOT NULL COMMENT '提供者地址 (IP:Port)',
    zk_path VARCHAR(500) COMMENT 'ZooKeeper节点路径',
    
    -- 时间信息
    register_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    last_heartbeat DATETIME COMMENT '最后心跳时间',
    last_sync_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最后同步时间',
    gmt_created DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 外键约束
    FOREIGN KEY (service_id) REFERENCES dubbo_services(id) ON DELETE CASCADE,
    
    -- 索引优化
    INDEX idx_service_id (service_id),
    INDEX idx_address (address),
    INDEX idx_register_time (register_time),
    INDEX idx_last_sync_time (last_sync_time),
    UNIQUE KEY uk_zk_path (zk_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务节点信息表';

-- 审批日志表
-- 记录审批历史，便于审计和追踪
CREATE TABLE IF NOT EXISTS approval_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    service_id BIGINT NOT NULL COMMENT '关联的服务ID',
    old_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL COMMENT '原审批状态',
    new_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL COMMENT '新审批状态',
    approver VARCHAR(100) NOT NULL COMMENT '审批人',
    approval_comment TEXT COMMENT '审批意见',
    gmt_created DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    -- 外键约束
    FOREIGN KEY (service_id) REFERENCES dubbo_services(id) ON DELETE CASCADE,
    
    -- 索引优化
    INDEX idx_service_id (service_id),
    INDEX idx_created_at (gmt_created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批日志表';

-- Dubbo服务方法信息表
-- 存储服务接口的方法信息，包括方法名、参数类型、返回值类型等
CREATE TABLE IF NOT EXISTS dubbo_service_methods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    
    -- 关联信息
    service_id BIGINT NOT NULL COMMENT '关联的服务ID',
    
    -- 方法基本信息
    method_name VARCHAR(255) NOT NULL COMMENT '方法名',
    return_type VARCHAR(255) COMMENT '返回值类型',
    
    -- 时间信息
    gmt_created DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 外键约束
    FOREIGN KEY (service_id) REFERENCES dubbo_services(id) ON DELETE CASCADE,
    
    -- 索引优化
    INDEX idx_service_id (service_id),
    INDEX idx_method_name (method_name),
    UNIQUE KEY uk_service_method (service_id, method_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务方法信息表';

-- Dubbo服务方法参数信息表
-- 存储服务方法的参数信息，包括参数名、参数类型、参数顺序等
CREATE TABLE IF NOT EXISTS dubbo_method_parameters (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    
    -- 关联信息
    method_id BIGINT NOT NULL COMMENT '关联的方法ID',
    
    -- 参数基本信息
    parameter_name VARCHAR(255) COMMENT '参数名',
    parameter_type VARCHAR(255) NOT NULL COMMENT '参数类型',
    parameter_order INT NOT NULL COMMENT '参数顺序',
    parameter_description TEXT COMMENT '参数描述',
    
    -- 时间信息
    gmt_created DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    -- 外键约束
    FOREIGN KEY (method_id) REFERENCES dubbo_service_methods(id) ON DELETE CASCADE,
    
    -- 索引优化
    INDEX idx_method_id (method_id),
    INDEX idx_parameter_order (parameter_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务方法参数信息表';