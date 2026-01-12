-- 接口白名单表
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

-- 插入默认白名单数据（可选）
-- INSERT INTO `zk_interface_whitelist` (`prefix`, `description`, `enabled`, `created_by`) 
-- VALUES ('com.zkinfo.demo', 'Demo项目接口白名单', 1, 'system');





