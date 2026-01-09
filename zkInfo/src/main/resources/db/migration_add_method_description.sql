use mcp_bridge;

-- Add method description column to store human-maintained docs for each Dubbo method
ALTER TABLE `zk_dubbo_service_method`
  ADD COLUMN `method_description` TEXT COMMENT '方法描述（人工维护）' AFTER `return_type`;

