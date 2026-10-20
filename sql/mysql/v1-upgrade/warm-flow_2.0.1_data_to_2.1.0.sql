-- ============================================================
-- Warm-Flow 2.0.1 → 2.1.0 状态编码数据迁移脚本（MySQL，**可选、仅需按需执行**）
--
-- 用途：2.1.0 把状态拆成「实例状态 / 任务状态 / 协作方式」三个独立维度，并把落库编码从
--       数字改成小写英文。引擎的增量脚本 warm-flow_2.1.0.sql 只同步列类型与注释、
--       不含任何数据迁移（引擎视角下 2.1.0 未发布、无存量），本脚本补上这一段。
--
-- 谁需要执行：**已经按 2.0.1 或更早版本落过库的宿主**（例如 RuoYi-Vue-Plus 这类已上线集成）。
--       全新建库（直接跑 2.1.0 全量脚本）不需要执行本脚本。
--
-- 前置条件：先执行同目录 warm-flow_2.1.0.sql（列类型必须先变成 varchar，
--       否则 `cooperate_type` 还是 tinyint 时本脚本的字符串写入会失败）。
--
-- 影响范围：flow_instance.flow_status、flow_task.flow_status、flow_his_task.flow_status、
--       flow_his_task.cooperate_type 四列的**存量数据值**。不改结构、不改代码、不动其它表。
--
-- 幂等性：每条 UPDATE 都用 WHERE 限定「旧数字码集合」，迁移后的行不再命中条件，可安全重跑。
--
-- 回滚：本脚本不可用 SQL 直接回滚（原值被覆盖）。执行前务必先跑下方「第 1 步 备份」，
--       回滚方式为从备份表按主键回写。
--
-- 各库兼容性：本文件仅 MySQL。Oracle / PostgreSQL / SQL Server 的映射关系完全相同，
--       差异只在 UPDATE 语法与字符串/类型，需要时按同样的 CASE 结构改写；
--       MySQL 的 `UPDATE ... SET x = CASE ... END WHERE x IN (...)` 在四库均可直译。
--
-- 执行需明确批准：这是批量改写业务数据的脚本，请先在测试库跑通并核对第 4 步的复验结果。
-- ============================================================

-- ---------- 第 0 步：审计（先看真实存量，再决定要不要跑） ----------
-- 期望：数字码与英文码可能混存（宿主自己直接置位过英文码的库里两种都有）。
SELECT 'flow_instance' AS tb, flow_status AS code, COUNT(*) AS cnt
FROM flow_instance GROUP BY flow_status ORDER BY cnt DESC;

SELECT 'flow_task' AS tb, flow_status AS code, COUNT(*) AS cnt
FROM flow_task GROUP BY flow_status ORDER BY cnt DESC;

SELECT 'flow_his_task' AS tb, flow_status AS code, COUNT(*) AS cnt
FROM flow_his_task GROUP BY flow_status ORDER BY cnt DESC;

SELECT 'his.cooperate_type' AS tb, cooperate_type AS code, COUNT(*) AS cnt
FROM flow_his_task GROUP BY cooperate_type ORDER BY cnt DESC;

-- ---------- 第 1 步：备份 ----------
CREATE TABLE IF NOT EXISTS bak_flow_instance_2_0_1     LIKE flow_instance;
CREATE TABLE IF NOT EXISTS bak_flow_task_2_0_1         LIKE flow_task;
CREATE TABLE IF NOT EXISTS bak_flow_his_task_2_0_1     LIKE flow_his_task;
INSERT INTO bak_flow_instance_2_0_1 SELECT * FROM flow_instance;
INSERT INTO bak_flow_task_2_0_1     SELECT * FROM flow_task;
INSERT INTO bak_flow_his_task_2_0_1 SELECT * FROM flow_his_task;

-- ---------- 第 2 步：实例维度 flow_instance.flow_status ----------
-- 映射：0待提交→draft 1审批中→waiting 4终止→termination 5作废→invalid 6撤销→cancel
--       8已完成→finish 9已退回→back 12重启→restart
--       7取回→waiting（取回已从实例维度移除，属任务维度 take_back；取回后实例仍在审批中）
--       13暂存→draft（暂存已从实例维度移除，与待提交合并）
UPDATE flow_instance
SET flow_status = CASE flow_status
                      WHEN '0' THEN 'draft'
                      WHEN '1' THEN 'waiting'
                      WHEN '4' THEN 'termination'
                      WHEN '5' THEN 'invalid'
                      WHEN '6' THEN 'cancel'
                      WHEN '7' THEN 'waiting'
                      WHEN '8' THEN 'finish'
                      WHEN '9' THEN 'back'
                      WHEN '12' THEN 'restart'
                      WHEN '13' THEN 'draft'
    END
WHERE flow_status IN ('0', '1', '4', '5', '6', '7', '8', '9', '12', '13');

-- ---------- 第 3 步：任务维度 flow_task / flow_his_task.flow_status ----------
-- 映射：1待办理→pending 2通过→pass 8结束→pass 9退回→back 10失效→invalid 11拿回→take_back
--       任务维度已移除「结束」：结束事件的留痕统一按「通过」归档，流程是否结束由实例状态表达。
--       待办表里若仍有存量待办（1），迁移后仍是待办，语义不变。
UPDATE flow_task
SET flow_status = CASE flow_status
                      WHEN '1' THEN 'pending'
                      WHEN '2' THEN 'pass'
                      WHEN '8' THEN 'pass'
                      WHEN '9' THEN 'back'
                      WHEN '10' THEN 'invalid'
                      WHEN '11' THEN 'take_back'
    END
WHERE flow_status IN ('1', '2', '8', '9', '10', '11');

UPDATE flow_his_task
SET flow_status = CASE flow_status
                      WHEN '1' THEN 'pending'
                      WHEN '2' THEN 'pass'
                      WHEN '8' THEN 'pass'
                      WHEN '9' THEN 'back'
                      WHEN '10' THEN 'invalid'
                      WHEN '11' THEN 'take_back'
    END
WHERE flow_status IN ('1', '2', '8', '9', '10', '11');

-- ---------- 第 4 步：协作维度 flow_his_task.cooperate_type ----------
-- 映射：0（旧 DDL 默认）/1无→none 2转办→transfer 3委派→delegate 6加签→add_signer 7减签→remove_signer
--       4会签 / 5票签→none：新模型里会签与票签由 flow_node.node_ratio 表达、抄送由
--       flow_status=copy 表达，都不再落这一列。**这一步会丢弃「该历史当初按会签/票签写入」
--       这一信息**，若宿主报表依赖 cooperate_type=4/5 统计会签票签，需改用 node_ratio 口径。
UPDATE flow_his_task
SET cooperate_type = CASE cooperate_type
                         WHEN '0' THEN 'none'
                         WHEN '1' THEN 'none'
                         WHEN '2' THEN 'transfer'
                         WHEN '3' THEN 'delegate'
                         WHEN '4' THEN 'none'
                         WHEN '5' THEN 'none'
                         WHEN '6' THEN 'add_signer'
                         WHEN '7' THEN 'remove_signer'
    END
WHERE cooperate_type IN ('0', '1', '2', '3', '4', '5', '6', '7');

-- ---------- 第 5 步：复验 ----------
-- 期望：四个查询都**不应再出现纯数字**，cooperate_type 只应命中
--       none / transfer / delegate / add_signer / remove_signer 五个值。
SELECT flow_status, COUNT(*) FROM flow_instance GROUP BY flow_status;
SELECT flow_status, COUNT(*) FROM flow_task GROUP BY flow_status;
SELECT flow_status, COUNT(*) FROM flow_his_task GROUP BY flow_status;
SELECT cooperate_type, COUNT(*) FROM flow_his_task GROUP BY cooperate_type;

-- 残留检查：命中任意一条说明映射没覆盖到实际数据，需人工处理而不是继续上线。
SELECT 'flow_instance' AS tb, id, flow_status FROM flow_instance WHERE flow_status REGEXP '^[0-9]+$'
UNION ALL
SELECT 'flow_task', id, flow_status FROM flow_task WHERE flow_status REGEXP '^[0-9]+$'
UNION ALL
SELECT 'flow_his_task', id, flow_status FROM flow_his_task WHERE flow_status REGEXP '^[0-9]+$'
UNION ALL
SELECT 'flow_his_task.cooperate_type', id, cooperate_type FROM flow_his_task WHERE cooperate_type REGEXP '^[0-9]+$';

-- ---------- 回滚（按需手工执行，按主键从备份表回写） ----------
-- UPDATE flow_instance     i JOIN bak_flow_instance_2_0_1     b ON i.id = b.id SET i.flow_status    = b.flow_status;
-- UPDATE flow_task         t JOIN bak_flow_task_2_0_1         b ON t.id = b.id SET t.flow_status    = b.flow_status;
-- UPDATE flow_his_task     h JOIN bak_flow_his_task_2_0_1     b ON h.id = b.id SET h.flow_status    = b.flow_status,
--                                                                               h.cooperate_type = b.cooperate_type;
-- 确认无误后清理：DROP TABLE bak_flow_instance_2_0_1, bak_flow_task_2_0_1, bak_flow_his_task_2_0_1;
