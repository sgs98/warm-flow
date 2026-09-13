-- 节点版本由流程定义版本统一管理，删除 flow_node.version。
ALTER TABLE `flow_node` DROP COLUMN `version`;
