-- ============================================================
-- Warm-Flow 2.1.2 MySQL 增量升级脚本
-- 适用：从 2.1.1（或更早）升级到 2.1.2。
-- 变更：flow_user 新增顺序号；flow_node 新增多人审批办理顺序。
-- ============================================================

-- ---- 顺序会签：办理人显式顺序 ----
ALTER TABLE `flow_user`
    ADD COLUMN `seq_no` int DEFAULT NULL COMMENT '办理顺序号（同一任务内从 0 递增；顺序会签用，其余协作方式不参与逻辑）' AFTER `associated`;

-- ---- 多人审批：完成规则与办理顺序拆分 ----
ALTER TABLE `flow_node`
    ADD COLUMN `cooperate_type` varchar(20) DEFAULT NULL COMMENT '多人审批办理顺序（parallel并行 sequence顺序）' AFTER `node_ratio`;

-- 不回填存量数据：旧任务不是顺序会签，无需生成顺序号；
-- 旧节点 cooperate_type 为空时默认为并行，历史 node_ratio 的 @@sequence 后缀仍由运行时兼容读取。

-- ---- 回滚（按需执行） ----
-- ALTER TABLE `flow_node` DROP COLUMN `cooperate_type`;
-- ALTER TABLE `flow_user` DROP COLUMN `seq_no`;

-- ============================================================
-- 其它数据库方言（升级语句）
-- ------------------------------------------------------------
-- Oracle：
--   ALTER TABLE FLOW_USER ADD (SEQ_NO NUMBER(10));
--   COMMENT ON COLUMN FLOW_USER.SEQ_NO IS '办理顺序号（同一任务内从 0 递增；顺序会签用，其余协作方式不参与逻辑）';
--   ALTER TABLE FLOW_NODE ADD (COOPERATE_TYPE VARCHAR2(20));
--   COMMENT ON COLUMN FLOW_NODE.COOPERATE_TYPE IS '多人审批办理顺序（parallel并行 sequence顺序）';
--   存量数据保持 NULL，不执行回填。
--   回滚：ALTER TABLE FLOW_NODE DROP COLUMN COOPERATE_TYPE;
--         ALTER TABLE FLOW_USER DROP COLUMN SEQ_NO;
--
-- PostgreSQL：
--   ALTER TABLE flow_user ADD COLUMN seq_no int4 NULL;
--   COMMENT ON COLUMN flow_user.seq_no IS '办理顺序号（同一任务内从 0 递增；顺序会签用，其余协作方式不参与逻辑）';
--   ALTER TABLE flow_node ADD COLUMN cooperate_type varchar(20) NULL;
--   COMMENT ON COLUMN flow_node.cooperate_type IS '多人审批办理顺序（parallel并行 sequence顺序）';
--   存量数据保持 NULL，不执行回填。
--   回滚：ALTER TABLE flow_node DROP COLUMN cooperate_type;
--         ALTER TABLE flow_user DROP COLUMN seq_no;
--
-- SQL Server：
--   ALTER TABLE flow_user ADD seq_no int NULL;
--   ALTER TABLE flow_node ADD cooperate_type nvarchar(20) NULL;
--   存量数据保持 NULL，不执行回填。
--   回滚：ALTER TABLE flow_node DROP COLUMN cooperate_type;
--         ALTER TABLE flow_user DROP COLUMN seq_no;
-- ============================================================
