drop table if exists zk_approval_log;
drop table if exists zk_dubbo_method_parameter;
drop table if exists zk_dubbo_service;
drop table if exists zk_dubbo_service_method;
drop table if exists zk_dubbo_service_node;
drop table if exists zk_interface_whitelist;
drop table if exists zk_project;
drop table if exists zk_project_service;
drop table if exists zk_service_approval;
drop table if exists zk_virtual_project_endpoint;   
CREATE TABLE `zk_approval_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `provider_id` bigint DEFAULT NULL COMMENT '关联的服务提供者ID',
  `service_id` bigint DEFAULT NULL COMMENT '关联的服务ID（可选）',
  `old_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原审批状态',
  `new_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '新审批状态',
  `approver` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人',
  `approval_comment` text COLLATE utf8mb4_unicode_ci COMMENT '审批意见',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_provider_id` (`provider_id`),
  KEY `idx_service_id` (`service_id`),
  KEY `idx_created_at` (`gmt_created`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批日志表';

CREATE TABLE `zk_dubbo_method_parameter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `method_id` bigint NOT NULL COMMENT '关联的方法ID',
  `interface_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务接口名（便于定位问题）',
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务版本（从 zk_dubbo_service 获取）',
  `parameter_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '参数名',
  `parameter_type` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '参数类型',
  `parameter_order` int NOT NULL COMMENT '参数顺序',
  `parameter_description` text COLLATE utf8mb4_unicode_ci COMMENT '参数描述',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_method_param_order` (`method_id`,`parameter_order`),
  KEY `idx_method_id` (`method_id`),
  KEY `idx_parameter_order` (`method_id`,`parameter_order`),
  KEY `idx_interface_name` (`interface_name`),
  KEY `idx_version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo方法参数表';

CREATE TABLE `zk_dubbo_service` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `interface_name` varchar(250) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务接口名',
  `protocol` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '协议类型',
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务版本',
  `group` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务分组',
  `application` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '应用名称',
  `approval_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INIT' COMMENT '审批状态: INIT-初始化, PENDING-待审批, APPROVED-已审批, REJECTED-已拒绝',
  `approver` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人',
  `approval_time` datetime DEFAULT NULL COMMENT '审批时间',
  `approval_comment` text COLLATE utf8mb4_unicode_ci COMMENT '审批意见',
  `provider_count` int NOT NULL DEFAULT '0' COMMENT '该服务下的Provider数量',
  `online_provider_count` int NOT NULL DEFAULT '0' COMMENT '该服务下在线的Provider数量',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service` (`interface_name`,`protocol`,`version`,`group`,`application`),
  KEY `idx_interface_name` (`interface_name`),
  KEY `idx_application` (`application`),
  KEY `idx_approval_status` (`approval_status`),
  KEY `idx_service_key` (`interface_name`,`protocol`,`version`,`group`,`application`)
) ENGINE=InnoDB AUTO_INCREMENT=382 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务表';

CREATE TABLE `zk_dubbo_service_method` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_id` bigint NOT NULL COMMENT '关联的服务ID',
  `interface_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务接口名（便于定位问题）',
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务版本（从 zk_dubbo_service 获取）',
  `method_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '方法名',
  `return_type` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '返回值类型',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_method` (`service_id`,`method_name`),
  KEY `idx_service_id` (`service_id`),
  KEY `idx_method_name` (`method_name`),
  KEY `idx_interface_name` (`interface_name`),
  KEY `idx_version` (`version`)
) ENGINE=InnoDB AUTO_INCREMENT=5869 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务方法表';

CREATE TABLE `zk_dubbo_service_node` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `service_id` bigint NOT NULL COMMENT '关联的服务ID',
  `interface_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务接口名（便于定位问题）',
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务版本（从 zk_dubbo_service 获取）',
  `address` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供者地址 (IP:Port)',
  `register_time` datetime DEFAULT NULL COMMENT '注册时间',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '最后心跳时间',
  `last_sync_time` datetime DEFAULT NULL COMMENT '最后同步时间',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `registration_time` datetime DEFAULT NULL COMMENT '注册时间',
  `last_heartbeat_time` datetime DEFAULT NULL COMMENT '最后心跳时间',
  `is_online` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否在线',
  `is_healthy` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否健康',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_address` (`service_id`,`address`),
  KEY `idx_service_id` (`service_id`),
  KEY `idx_address` (`address`),
  KEY `idx_register_time` (`register_time`),
  KEY `idx_last_sync_time` (`last_sync_time`),
  KEY `idx_interface_name` (`interface_name`),
  KEY `idx_version` (`version`),
  KEY `idx_is_online` (`is_online`),
  KEY `idx_is_healthy` (`is_healthy`),
  KEY `idx_last_heartbeat` (`last_heartbeat_time`)
) ENGINE=InnoDB AUTO_INCREMENT=361 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dubbo服务节点表';

CREATE TABLE `zk_interface_whitelist` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prefix` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接口名前缀（左匹配），例如：com.zkinfo.demo',
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '白名单描述',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用：1-启用，0-禁用',
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '创建人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '更新人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prefix` (`prefix`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_prefix` (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口白名单表';

CREATE TABLE `zk_project` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  `project_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目代码（唯一标识）',
  `project_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `project_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'REAL' COMMENT '项目类型：REAL（实际项目）, VIRTUAL（虚拟项目）',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '项目描述',
  `owner_id` bigint DEFAULT NULL COMMENT '项目负责人ID',
  `owner_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目负责人姓名',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE, DELETED',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  KEY `idx_project_type` (`project_type`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

CREATE TABLE `zk_project_service` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `project_id` bigint NOT NULL COMMENT '项目ID',
  `service_interface` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务接口（完整路径）',
  `service_version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务版本',
  `service_group` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务分组',
  `priority` int DEFAULT '0' COMMENT '优先级（虚拟项目中用于排序）',
  `enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `added_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '添加时间',
  `added_by` bigint DEFAULT NULL COMMENT '添加人ID',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_service_interface` (`service_interface`),
  KEY `idx_service_key` (`service_interface`,`service_version`,`service_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目服务关联表';

CREATE TABLE `zk_service_approval` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审批ID',
  `service_interface` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '服务接口名',
  `service_version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务版本',
  `service_group` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '服务分组',
  `project_id` bigint DEFAULT NULL COMMENT '项目ID（可选）',
  `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `applicant_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '申请人姓名',
  `reason` text COLLATE utf8mb4_unicode_ci COMMENT '申请原因',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '审批状态：PENDING-待审批, APPROVED-已通过, REJECTED-已拒绝, CANCELLED-已取消',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `approver_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名',
  `comment` text COLLATE utf8mb4_unicode_ci COMMENT '审批意见',
  `approved_at` datetime DEFAULT NULL COMMENT '审批时间',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_service_interface` (`service_interface`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_status` (`status`),
  KEY `idx_applicant_id` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务审批表';

CREATE TABLE `zk_virtual_project_endpoint` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '映射ID',
  `virtual_project_id` bigint NOT NULL COMMENT '虚拟项目ID',
  `endpoint_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Endpoint名称（对应mcp-router-v3的serviceName）',
  `endpoint_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Endpoint路径（如：/sse/{endpointName}）',
  `mcp_service_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MCP服务名称（注册到Nacos的名称）',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT 'Endpoint描述',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE, INACTIVE',
  `gmt_created` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_endpoint_name` (`endpoint_name`),
  KEY `idx_virtual_project_id` (`virtual_project_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='虚拟项目端点表';
SELECT * FROM mcp_bridge.zk_virtual_project_endpoint;