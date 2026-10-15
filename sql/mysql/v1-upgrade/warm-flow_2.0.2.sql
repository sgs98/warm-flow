-- 节点配置列：服务任务等节点在 flow_node 上持久化 node_config，历史任务快照同一份配置。
ALTER TABLE `flow_node`
    ADD COLUMN `node_config` text NULL COMMENT '节点配置（服务任务等引擎配置与自定义扩展属性json）' AFTER `ext`;

ALTER TABLE `flow_his_task`
    ADD COLUMN `node_config` text NULL COMMENT '节点配置（服务任务等引擎配置与自定义扩展属性json）' AFTER `ext`;

-- 流程定义配置列：自动审批等定义级引擎配置从业务 ext 迁至 def_config，ext 回归业务系统独占。
-- 引擎只读 def_config 中的 autoPass，不回退读 ext。
ALTER TABLE `flow_definition`
    ADD COLUMN `def_config` text NULL COMMENT '流程定义配置（自动审批等引擎配置与自定义扩展属性json）' AFTER `ext`;
