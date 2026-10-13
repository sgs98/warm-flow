-- 节点配置列：服务任务等节点在 flow_node 上持久化 node_config，历史任务快照同一份配置。
ALTER TABLE `flow_node`
    ADD COLUMN `node_config` text NULL COMMENT '节点配置（服务任务等节点的引擎配置json）' AFTER `ext`;

ALTER TABLE `flow_his_task`
    ADD COLUMN `node_config` text NULL COMMENT '节点配置（服务任务等节点的引擎配置json）' AFTER `ext`;
