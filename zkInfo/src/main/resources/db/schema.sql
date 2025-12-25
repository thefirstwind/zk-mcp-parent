-- ============================================
-- zkInfo 数据库创建脚本
-- 表名统一使用 zk_ 开头
-- 创建日期: 2025-12-17
-- ============================================

-- 1. 项目表（实际项目 + 虚拟项目）
CREATE TABLE IF NOT EXISTS `zk_project` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '项目ID',
    `project_code` VARCHAR(100) NOT NULL COMMENT '项目代码（唯一标识）',
    `project_name` VARCHAR(200) NOT NULL COMMENT '项目名称',
    `project_type` VARCHAR(20) NOT NULL DEFAULT 'REAL' COMMENT '项目类型：REAL（实际项目）, VIRTUAL（虚拟项目）',
    `description` VARCHAR(2000) COMMENT '项目描述',
    `owner_id` BIGINT COMMENT '项目负责人ID',
    `owner_name` VARCHAR(100) COMMENT '项目负责人姓名',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE, DELETED',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_project_code` (`project_code`),
    KEY `idx_project_type` (`project_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- 2. 项目服务关联表
-- 注意：改为引用 zk_dubbo_service.id，避免重复存储服务信息
CREATE TABLE IF NOT EXISTS `zk_project_service` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    `project_id` BIGINT NOT NULL COMMENT '项目ID',
    `service_id` BIGINT NOT NULL COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）',
    `priority` INT DEFAULT 0 COMMENT '优先级（虚拟项目中用于排序）',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `added_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
    `added_by` BIGINT COMMENT '添加人ID',
    PRIMARY KEY (`id`),
    KEY `idx_project_id` (`project_id`),
    KEY `idx_service_id` (`service_id`),
    UNIQUE KEY `uk_project_service` (`project_id`, `service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目服务关联表';

-- 3. 虚拟项目端点表
CREATE TABLE IF NOT EXISTS `zk_virtual_project_endpoint` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '映射ID',
    `virtual_project_id` BIGINT NOT NULL COMMENT '虚拟项目ID',
    `endpoint_name` VARCHAR(200) NOT NULL COMMENT 'Endpoint名称（对应mcp-router-v3的serviceName）',
    `endpoint_path` VARCHAR(500) COMMENT 'Endpoint路径（如：/sse/{endpointName}）',
    `mcp_service_name` VARCHAR(200) COMMENT 'MCP服务名称（注册到Nacos的名称）',
    `description` VARCHAR(2000) COMMENT 'Endpoint描述',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_endpoint_name` (`endpoint_name`),
    KEY `idx_virtual_project_id` (`virtual_project_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='虚拟项目端点表';

-- 4. Dubbo服务表（按服务维度）
CREATE TABLE IF NOT EXISTS `zk_dubbo_service` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `interface_name` VARCHAR(250) NOT NULL COMMENT '服务接口名',
    `protocol` VARCHAR(50) COMMENT '协议类型',
    `version` VARCHAR(50) COMMENT '服务版本',
    `group` VARCHAR(100) COMMENT '服务分组',
    `application` VARCHAR(200) COMMENT '应用名称',
    `approval_status` VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '审批状态: INIT-初始化, PENDING-待审批, APPROVED-已审批, REJECTED-已拒绝, OFFLINE-已下线',
    `approver` VARCHAR(100) COMMENT '审批人',
    `approval_time` DATETIME COMMENT '审批时间',
    `approval_comment` VARCHAR(2000) COMMENT '审批意见',
    `provider_count` INT NOT NULL DEFAULT 0 COMMENT '该服务下的Provider数量',
    `online_provider_count` INT NOT NULL DEFAULT 0 COMMENT '该服务下在线的Provider数量',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_interface_name` (`interface_name`),
    KEY `idx_application` (`application`),
    KEY `idx_approval_status` (`approval_status`),
    KEY `idx_service_key` (`interface_name`, `protocol`, `version`, `group`, `application`),
    UNIQUE KEY `uk_service` (`interface_name`, `protocol`, `version`, `group`, `application`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务表';

-- 5. Provider信息表（服务提供者详细信息）
-- 注意：此表存储 Provider 的扩展信息（心跳、健康状态等）
-- interface_name 和 address 字段保留，便于快速查询和定位问题
-- 服务基本信息通过 service_id 关联 zk_dubbo_service 获取
-- 节点信息通过 node_id 关联 zk_dubbo_service_node 获取
-- 审批状态通过 service_id 关联 zk_dubbo_service.approval_status 获取
CREATE TABLE IF NOT EXISTS `zk_provider_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `service_id` BIGINT NOT NULL COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）',
    `node_id` BIGINT NOT NULL COMMENT '关联的服务节点ID（引用 zk_dubbo_service_node.id）',
    `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于快速查询和定位问题）',
    `address` VARCHAR(200) NOT NULL COMMENT '提供者地址 (IP:Port)（便于快速查询和定位问题）',
    `registration_time` DATETIME COMMENT '注册时间',
    `last_heartbeat_time` DATETIME COMMENT '最后心跳时间',
    `is_online` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否在线',
    `is_healthy` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否健康',
    `last_sync_time` DATETIME COMMENT '最后同步时间',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_node_id` (`node_id`),
    KEY `idx_interface_name` (`interface_name`),
    KEY `idx_address` (`address`),
    KEY `idx_is_online` (`is_online`),
    KEY `idx_is_healthy` (`is_healthy`),
    KEY `idx_last_heartbeat` (`last_heartbeat_time`),
    UNIQUE KEY `uk_service_node` (`service_id`, `node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Provider信息表';

-- 6. Dubbo服务节点表（服务实例节点信息）
-- 注意：此表不保存 zk_path，只保存结构化后的字段，便于查询和定位问题
CREATE TABLE IF NOT EXISTS `zk_dubbo_service_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `service_id` BIGINT NOT NULL COMMENT '关联的服务ID',
    `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于定位问题）',
    `version` VARCHAR(50) COMMENT '服务版本（从 zk_dubbo_service 获取）',
    `address` VARCHAR(200) NOT NULL COMMENT '提供者地址 (IP:Port)',
    `last_sync_time` DATETIME COMMENT '最后同步时间',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_address` (`service_id`, `address`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_interface_name` (`interface_name`),
    KEY `idx_version` (`version`),
    KEY `idx_address` (`address`),
    KEY `idx_last_sync_time` (`last_sync_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务节点表';

-- 7. Dubbo服务方法表
-- 注意：interface_name 和 version 是冗余字段，便于快速查询和定位问题，实际数据应通过 service_id 关联获取
CREATE TABLE IF NOT EXISTS `zk_dubbo_service_method` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `service_id` BIGINT NOT NULL COMMENT '关联的服务ID（引用 zk_dubbo_service.id）',
    `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（冗余字段，便于快速查询和定位问题）',
    `version` VARCHAR(50) COMMENT '服务版本（冗余字段，从 zk_dubbo_service 获取，便于快速查询）',
    `method_name` VARCHAR(200) NOT NULL COMMENT '方法名',
    `return_type` VARCHAR(500) COMMENT '返回值类型',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_interface_name` (`interface_name`),
    KEY `idx_version` (`version`),
    KEY `idx_method_name` (`method_name`),
    UNIQUE KEY `uk_service_method` (`service_id`, `method_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务方法表';

-- 8. Dubbo方法参数表
-- 注意：interface_name 和 version 是冗余字段，便于快速查询和定位问题，实际数据应通过 method_id -> service_id 关联获取
CREATE TABLE IF NOT EXISTS `zk_dubbo_method_parameter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `method_id` BIGINT NOT NULL COMMENT '关联的方法ID（引用 zk_dubbo_service_method.id）',
    `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（冗余字段，便于快速查询和定位问题）',
    `version` VARCHAR(50) COMMENT '服务版本（冗余字段，通过 method_id -> service_id 获取，便于快速查询）',
    `parameter_name` VARCHAR(200) COMMENT '参数名',
    `parameter_type` VARCHAR(500) NOT NULL COMMENT '参数类型',
    `parameter_order` INT NOT NULL COMMENT '参数顺序',
    `parameter_description` VARCHAR(2000) COMMENT '参数描述',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_method_id` (`method_id`),
    KEY `idx_interface_name` (`interface_name`),
    KEY `idx_version` (`version`),
    KEY `idx_parameter_order` (`method_id`, `parameter_order`),
    UNIQUE KEY `uk_method_param_order` (`method_id`, `parameter_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo方法参数表';

-- 9. 服务审批表（审批申请流程表）
-- 注意：改为引用 zk_dubbo_service.id，避免重复存储服务信息
-- 审批结果存储在 zk_dubbo_service.approval_status 中
CREATE TABLE IF NOT EXISTS `zk_service_approval` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批ID',
    `service_id` BIGINT NOT NULL COMMENT '关联的Dubbo服务ID（引用 zk_dubbo_service.id）',
    `project_id` BIGINT COMMENT '项目ID（可选）',
    `applicant_id` BIGINT COMMENT '申请人ID',
    `applicant_name` VARCHAR(100) COMMENT '申请人姓名',
    `reason` VARCHAR(2000) COMMENT '申请原因',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态：PENDING-待审批, APPROVED-已通过, REJECTED-已拒绝, CANCELLED-已取消',
    `approver_id` BIGINT COMMENT '审批人ID',
    `approver_name` VARCHAR(100) COMMENT '审批人姓名',
    `comment` VARCHAR(2000) COMMENT '审批意见',
    `approved_at` DATETIME COMMENT '审批时间',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_project_id` (`project_id`),
    KEY `idx_status` (`status`),
    KEY `idx_applicant_id` (`applicant_id`),
    UNIQUE KEY `uk_service_approval` (`service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务审批表';

-- 10. 审批日志表（审批历史记录，用于审计）
-- 注意：审批日志记录服务级别的审批历史，Provider 的审批跟随 Service
CREATE TABLE IF NOT EXISTS `zk_approval_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `service_id` BIGINT NOT NULL COMMENT '关联的服务ID（引用 zk_dubbo_service.id）',
    `approval_id` BIGINT COMMENT '关联的审批申请ID（引用 zk_service_approval.id，可选）',
    `old_status` VARCHAR(20) COMMENT '原审批状态',
    `new_status` VARCHAR(20) NOT NULL COMMENT '新审批状态',
    `approver` VARCHAR(100) COMMENT '审批人',
    `approval_comment` VARCHAR(2000) COMMENT '审批意见',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_id` (`service_id`),
    KEY `idx_approval_id` (`approval_id`),
    KEY `idx_created_at` (`gmt_created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批日志表';

-- 11. Provider方法表（Provider的方法列表，支持大文本）
-- 注意：由于生产环境限制，methods 字段不能超过 VARCHAR(2000)，因此使用子表存储
CREATE TABLE IF NOT EXISTS `zk_provider_method` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id` BIGINT NOT NULL COMMENT '关联的Provider ID',
    `method_name` VARCHAR(500) NOT NULL COMMENT '方法名',
    `method_order` INT COMMENT '方法顺序',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_provider_id` (`provider_id`),
    KEY `idx_method_name` (`method_name`),
    UNIQUE KEY `uk_provider_method` (`provider_id`, `method_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Provider方法表';

-- 12. Provider参数表（Provider的参数键值对，支持大文本）
-- 注意：由于生产环境限制，parameters 字段不能超过 VARCHAR(2000)，因此使用子表存储
CREATE TABLE IF NOT EXISTS `zk_provider_parameter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider_id` BIGINT NOT NULL COMMENT '关联的Provider ID',
    `param_key` VARCHAR(500) NOT NULL COMMENT '参数键',
    `param_value` VARCHAR(2000) COMMENT '参数值',
    `param_order` INT COMMENT '参数顺序',
    `gmt_created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_provider_id` (`provider_id`),
    KEY `idx_param_key` (`param_key`),
    UNIQUE KEY `uk_provider_param` (`provider_id`, `param_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Provider参数表';

-- 13. 接口白名单表
-- 用于控制哪些接口可以入库，只有 interface_name 左匹配白名单前缀的接口才准许入库
CREATE TABLE IF NOT EXISTS `zk_interface_whitelist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `prefix` VARCHAR(500) NOT NULL COMMENT '接口名前缀（左匹配），例如：com.zkinfo.demo',
    `description` VARCHAR(2000) COMMENT '白名单描述',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    `created_by` VARCHAR(100) COMMENT '创建人',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_by` VARCHAR(100) COMMENT '更新人',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_prefix` (`prefix`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_prefix` (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口白名单表';

-- ============================================
-- 外键约束（可选，根据实际需求决定是否启用）
-- 
-- 注意：外键约束可以提高数据完整性，但可能影响性能。
-- 如果启用外键，建议在应用层也做好数据校验，避免级联删除导致意外数据丢失。
-- ============================================

-- ALTER TABLE `zk_project_service` ADD CONSTRAINT `fk_project_service_project` 
--     FOREIGN KEY (`project_id`) REFERENCES `zk_project` (`id`) ON DELETE CASCADE;

-- ALTER TABLE `zk_virtual_project_endpoint` ADD CONSTRAINT `fk_virtual_endpoint_project` 
--     FOREIGN KEY (`virtual_project_id`) REFERENCES `zk_project` (`id`) ON DELETE CASCADE;

-- ALTER TABLE `zk_dubbo_service_node` ADD CONSTRAINT `fk_service_node_service` 
--     FOREIGN KEY (`service_id`) REFERENCES `zk_dubbo_service` (`id`) ON DELETE CASCADE;

-- ALTER TABLE `zk_dubbo_service_method` ADD CONSTRAINT `fk_service_method_service` 
--     FOREIGN KEY (`service_id`) REFERENCES `zk_dubbo_service` (`id`) ON DELETE CASCADE;

-- ALTER TABLE `zk_dubbo_method_parameter` ADD CONSTRAINT `fk_method_parameter_method` 
--     FOREIGN KEY (`method_id`) REFERENCES `zk_dubbo_service_method` (`id`) ON DELETE CASCADE;

-- ALTER TABLE `zk_approval_log` ADD CONSTRAINT `fk_approval_log_provider` 
--     FOREIGN KEY (`provider_id`) REFERENCES `zk_provider_info` (`id`) ON DELETE SET NULL;

-- ALTER TABLE `zk_approval_log` ADD CONSTRAINT `fk_approval_log_service` 
--     FOREIGN KEY (`service_id`) REFERENCES `zk_dubbo_service` (`id`) ON DELETE SET NULL;

