-- =============================================================
-- warm-flow-demo 业务表初始化
-- 数据库：warm-flow（需先手工创建）
-- 说明：
--  1) Warm-Flow 引擎的 7 张核心表（flow_definition/flow_node/flow_skip/
--     flow_instance/flow_task/flow_his_task/flow_user）请直接执行仓库规范脚本
--     sql/mysql/warm-flow-all.sql，本文件不再复制，避免与官方脚本漂移。
--  2) demo 用户与部门使用内存数据，不创建业务表。
-- 执行顺序：先 warm-flow-all.sql，再本文件，再 data.sql。
-- =============================================================

-- demo 流程分类表
CREATE TABLE IF NOT EXISTS `demo_category`
(
    `id`
    bigint
    NOT
    NULL
    COMMENT
    '主键id',
    `code`
    varchar
(
    40
) NOT NULL COMMENT '分类编码',
    `name` varchar
(
    64
) NOT NULL COMMENT '分类名称',
    `del_flag` char
(
    1
) DEFAULT '0' COMMENT '删除标志',
    PRIMARY KEY
(
    `id`
)
    ) ENGINE = InnoDB COMMENT ='demo流程分类表';
