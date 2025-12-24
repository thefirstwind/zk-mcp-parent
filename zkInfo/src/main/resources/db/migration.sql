use mcp_bridge;
ALTER TABLE `zk_provider_info` 
    ADD COLUMN  `path_root` VARCHAR(200) COMMENT '路径根（如：/dubbo）' AFTER `zk_path`,
    ADD COLUMN `path_interface` VARCHAR(500) COMMENT '路径中的接口名' AFTER `path_root`,
    ADD COLUMN  `path_address` VARCHAR(200) COMMENT '路径中的地址（IP:Port）' AFTER `path_interface`,
    ADD COLUMN  `path_protocol` VARCHAR(50) COMMENT '路径中的协议' AFTER `path_address`,
    ADD COLUMN  `path_version` VARCHAR(50) COMMENT '路径中的版本' AFTER `path_protocol`,
    ADD COLUMN  `path_group` VARCHAR(100) COMMENT '路径中的分组' AFTER `path_version`,
    ADD COLUMN  `path_application` VARCHAR(200) COMMENT '路径中的应用名' AFTER `path_group`;
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

