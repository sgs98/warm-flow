-- flow_instance.node_code 长度与节点编码对齐，由 varchar(40) 扩至 varchar(100)。
ALTER TABLE `flow_instance` MODIFY COLUMN `node_code` varchar(100) NOT NULL COMMENT '流程节点编码';
