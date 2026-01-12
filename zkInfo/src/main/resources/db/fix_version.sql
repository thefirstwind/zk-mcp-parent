-- ============================================
-- 修复 zk_dubbo_service_node、zk_dubbo_service_method、zk_dubbo_method_parameter 
-- 表中 version 字段为空的记录
-- ============================================

USE mcp_bridge;

-- 1. 修复 zk_dubbo_service_node 表中 version 为空的记录
-- 从 zk_dubbo_service 表获取 version 并更新
UPDATE `zk_dubbo_service_node` n
INNER JOIN `zk_dubbo_service` s ON n.service_id = s.id
SET n.version = s.version
WHERE (n.version IS NULL OR n.version = '') 
  AND s.version IS NOT NULL 
  AND s.version != '';

-- 查询修复结果
SELECT 
    COUNT(*) as total_nodes,
    SUM(CASE WHEN version IS NULL OR version = '' THEN 1 ELSE 0 END) as null_version_count
FROM zk_dubbo_service_node;

-- 2. 修复 zk_dubbo_service_method 表中 version 为空的记录
UPDATE `zk_dubbo_service_method` m
INNER JOIN `zk_dubbo_service` s ON m.service_id = s.id
SET m.version = s.version
WHERE (m.version IS NULL OR m.version = '') 
  AND s.version IS NOT NULL 
  AND s.version != '';

-- 查询修复结果
SELECT 
    COUNT(*) as total_methods,
    SUM(CASE WHEN version IS NULL OR version = '' THEN 1 ELSE 0 END) as null_version_count
FROM zk_dubbo_service_method;

-- 3. 修复 zk_dubbo_method_parameter 表中 version 为空的记录
UPDATE `zk_dubbo_method_parameter` p
INNER JOIN `zk_dubbo_service_method` m ON p.method_id = m.id
INNER JOIN `zk_dubbo_service` s ON m.service_id = s.id
SET p.version = s.version
WHERE (p.version IS NULL OR p.version = '') 
  AND s.version IS NOT NULL 
  AND s.version != '';

-- 查询修复结果
SELECT 
    COUNT(*) as total_parameters,
    SUM(CASE WHEN version IS NULL OR version = '' THEN 1 ELSE 0 END) as null_version_count
FROM zk_dubbo_method_parameter;

-- 4. 查询无法修复的记录（service 不存在或 service 的 version 也为空）
-- 这些记录可能需要人工处理或从 ProviderInfo 中获取 version

-- 4.1 查询 zk_dubbo_service_node 中无法修复的记录
SELECT 
    n.id,
    n.service_id,
    n.interface_name,
    n.address,
    CASE 
        WHEN s.id IS NULL THEN '服务不存在'
        WHEN s.version IS NULL OR s.version = '' THEN '服务的 version 为空'
        ELSE '其他原因'
    END as reason
FROM zk_dubbo_service_node n
LEFT JOIN zk_dubbo_service s ON n.service_id = s.id
WHERE (n.version IS NULL OR n.version = '')
  AND (s.id IS NULL OR s.version IS NULL OR s.version = '');

-- 4.2 查询 zk_dubbo_service_method 中无法修复的记录
SELECT 
    m.id,
    m.service_id,
    m.interface_name,
    m.method_name,
    CASE 
        WHEN s.id IS NULL THEN '服务不存在'
        WHEN s.version IS NULL OR s.version = '' THEN '服务的 version 为空'
        ELSE '其他原因'
    END as reason
FROM zk_dubbo_service_method m
LEFT JOIN zk_dubbo_service s ON m.service_id = s.id
WHERE (m.version IS NULL OR m.version = '')
  AND (s.id IS NULL OR s.version IS NULL OR s.version = '');

-- 4.3 查询 zk_dubbo_method_parameter 中无法修复的记录
SELECT 
    p.id,
    p.method_id,
    p.interface_name,
    m.service_id,
    CASE 
        WHEN m.id IS NULL THEN '方法不存在'
        WHEN s.id IS NULL THEN '服务不存在'
        WHEN s.version IS NULL OR s.version = '' THEN '服务的 version 为空'
        ELSE '其他原因'
    END as reason
FROM zk_dubbo_method_parameter p
LEFT JOIN zk_dubbo_service_method m ON p.method_id = m.id
LEFT JOIN zk_dubbo_service s ON m.service_id = s.id
WHERE (p.version IS NULL OR p.version = '')
  AND (m.id IS NULL OR s.id IS NULL OR s.version IS NULL OR s.version = '');





