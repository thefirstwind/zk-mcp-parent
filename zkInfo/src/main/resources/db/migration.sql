use mcp_bridge;

-- 1. 移除 zk_provider_info 表中的 zk_path 和 path_version 字段（如果存在）
-- 注意：这些字段没有实际意义，不再保存
ALTER TABLE `zk_provider_info` 
    DROP COLUMN IF EXISTS `zk_path`,
    DROP COLUMN IF EXISTS `path_version`;

-- 2. 添加结构化路径字段（如果不存在）
ALTER TABLE `zk_provider_info` 
    ADD COLUMN IF NOT EXISTS `path_root` VARCHAR(200) COMMENT '路径根（如：/dubbo）' AFTER `application`,
    ADD COLUMN IF NOT EXISTS `path_interface` VARCHAR(500) COMMENT '路径中的接口名' AFTER `path_root`,
    ADD COLUMN IF NOT EXISTS `path_address` VARCHAR(200) COMMENT '路径中的地址（IP:Port）' AFTER `path_interface`,
    ADD COLUMN IF NOT EXISTS `path_protocol` VARCHAR(50) COMMENT '路径中的协议' AFTER `path_address`,
    ADD COLUMN IF NOT EXISTS `path_group` VARCHAR(100) COMMENT '路径中的分组' AFTER `path_protocol`,
    ADD COLUMN IF NOT EXISTS `path_application` VARCHAR(200) COMMENT '路径中的应用名' AFTER `path_group`;

-- 3. 创建子表用于存储 methods 和 parameters（由于生产环境限制 VARCHAR(2000)）
-- 3.1 创建 zk_provider_method 表
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

-- 3.2 创建 zk_provider_parameter 表
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

-- 3.3 移除 methods 和 parameters 字段（已移至子表）
ALTER TABLE `zk_provider_info` 
    DROP COLUMN `methods`,
    DROP COLUMN  `parameters`;
ALTER TABLE `zk_dubbo_service_node` 
    DROP COLUMN `zk_path`;

-- 3. 确保 zk_dubbo_service_node 表有 interface_name 字段（如果不存在）
ALTER TABLE `zk_dubbo_service_node` 
    ADD COLUMN  `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于定位问题）' AFTER `service_id`;

-- 4. 更新索引（移除 zk_path 相关索引，添加 interface_name 索引）
-- 注意：如果索引不存在，这些语句会报错，可以忽略
ALTER TABLE `zk_dubbo_service_node` 
    DROP INDEX `idx_zk_path`;

-- 确保 interface_name 索引存在
ALTER TABLE `zk_dubbo_service_node` 
    ADD INDEX  `idx_interface_name` (`interface_name`);

-- 添加唯一索引支持批量插入的 ON DUPLICATE KEY UPDATE
ALTER TABLE `zk_dubbo_service_node` 
     ADD UNIQUE INDEX  `uk_service_address` (`service_id`, `address`);

-- 5. 确保 zk_dubbo_service_method 表有 interface_name 字段
ALTER TABLE `zk_dubbo_service_method` 
     ADD COLUMN  `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于定位问题）' AFTER `service_id`;

-- 确保 interface_name 索引存在
ALTER TABLE `zk_dubbo_service_method` 
     ADD INDEX  `idx_interface_name` (`interface_name`);

-- 6. 确保 zk_dubbo_method_parameter 表有 interface_name 字段
ALTER TABLE `zk_dubbo_method_parameter` 
     ADD COLUMN  `interface_name` VARCHAR(500) NOT NULL COMMENT '服务接口名（便于定位问题）' AFTER `method_id`;

-- 确保 interface_name 索引存在
ALTER TABLE `zk_dubbo_method_parameter` 
     ADD INDEX  `idx_interface_name` (`interface_name`);

-- 7. 为三个表添加 version 字段
-- 7.1 zk_dubbo_service_node 表添加 version 字段
ALTER TABLE `zk_dubbo_service_node` 
     ADD COLUMN  `version` VARCHAR(50) COMMENT '服务版本（从 zk_dubbo_service 获取）' AFTER `interface_name`;

-- 添加 version 索引
ALTER TABLE `zk_dubbo_service_node` 
     ADD INDEX  `idx_version` (`version`);

-- 7.2 zk_dubbo_service_method 表添加 version 字段
ALTER TABLE `zk_dubbo_service_method` 
     ADD COLUMN  `version` VARCHAR(50) COMMENT '服务版本（从 zk_dubbo_service 获取）' AFTER `interface_name`;

-- 添加 version 索引
ALTER TABLE `zk_dubbo_service_method` 
     ADD INDEX  `idx_version` (`version`);

-- 7.3 zk_dubbo_method_parameter 表添加 version 字段
ALTER TABLE `zk_dubbo_method_parameter` 
     ADD COLUMN  `version` VARCHAR(50) COMMENT '服务版本（从 zk_dubbo_service 获取）' AFTER `interface_name`;

-- 添加 version 索引
ALTER TABLE `zk_dubbo_method_parameter` 
     ADD INDEX  `idx_version` (`version`);

-- 8. 数据迁移：从 zk_dubbo_service 表更新 version 字段
-- 注意：此操作需要根据 service_id 关联更新
UPDATE `zk_dubbo_service_node` n
INNER JOIN `zk_dubbo_service` s ON n.service_id = s.id
SET n.version = s.version
WHERE n.version IS NULL;

UPDATE `zk_dubbo_service_method` m
INNER JOIN `zk_dubbo_service` s ON m.service_id = s.id
SET m.version = s.version
WHERE m.version IS NULL;

UPDATE `zk_dubbo_method_parameter` p
INNER JOIN `zk_dubbo_service_method` m ON p.method_id = m.id
INNER JOIN `zk_dubbo_service` s ON m.service_id = s.id
SET p.version = s.version
WHERE p.version IS NULL;

-- 9. 为 zk_provider_info 表添加 service_id 和 node_id 字段（冗余移除迁移）
-- 9.1 检查并添加 service_id 字段
SET @dbname = DATABASE();
SET @tablename = 'zk_provider_info';
SET @columnname = 'service_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' BIGINT NOT NULL COMMENT ''关联的Dubbo服务ID（引用 zk_dubbo_service.id）'' AFTER `id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 9.2 检查并添加 node_id 字段
SET @columnname = 'node_id';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (column_name = @columnname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname, ' BIGINT NOT NULL COMMENT ''关联的服务节点ID（引用 zk_dubbo_service_node.id）'' AFTER `service_id`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 9.3 添加唯一索引（如果不存在）
SET @indexname = 'uk_service_node';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE
      (table_name = @tablename)
      AND (table_schema = @dbname)
      AND (index_name = @indexname)
  ) > 0,
  'SELECT 1',
  CONCAT('ALTER TABLE ', @tablename, ' ADD UNIQUE INDEX ', @indexname, ' (`service_id`, `node_id`)')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;





-- 5. 数据迁移：从 zk_path 解析并填充结构化字段
-- 注意：此操作需要应用层代码执行，这里仅提供 SQL 示例
-- 实际迁移应该通过应用代码的 ZkPathParser 工具类来完成

-- 示例：更新 path_interface 字段（从 zk_path 解析）
-- UPDATE zk_provider_info 
-- SET path_interface = SUBSTRING_INDEX(SUBSTRING_INDEX(zk_path, '/', 3), '/', -1)
-- WHERE path_interface IS NULL AND zk_path IS NOT NULL;

-- ============================================
-- 回滚脚本（如果需要回滚）
-- ============================================
-- 
-- -- 恢复 zk_dubbo_service_node 表的 zk_path 字段
-- ALTER TABLE `zk_dubbo_service_node` 
--     ADD COLUMN `zk_path` VARCHAR(2000) COMMENT 'ZooKeeper节点路径' AFTER `address`;
-- 
-- -- 恢复索引
-- ALTER TABLE `zk_dubbo_service_node` 
--     ADD INDEX `idx_zk_path` (`zk_path`);
-- 
-- -- 移除结构化字段（可选）
-- ALTER TABLE `zk_provider_info` 
--     DROP COLUMN `path_root`,
--     DROP COLUMN `path_interface`,
--     DROP COLUMN `path_address`,
--     DROP COLUMN `path_protocol`,
--     DROP COLUMN `path_version`,
--     DROP COLUMN `path_group`,
--     DROP COLUMN `path_application`;
-- 
-- ============================================

