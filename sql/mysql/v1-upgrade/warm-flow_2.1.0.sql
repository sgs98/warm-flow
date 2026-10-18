-- 节点事件列：事件节点在 flow_node 上持久化事件类型与消息名称；事件 JSON 配置复用 node_config 的 event 段，不新增 event_config 列。
-- 兼容：旧数据四个事件列均为 NULL，表示无事件，既有流程行为不变；固定 START / END 不承载事件。
ALTER TABLE `flow_node`
    ADD COLUMN `event_type` varchar(40) NULL COMMENT '事件类型（MESSAGE_START TIMER_START TIMER_CATCH MESSAGE_CATCH MESSAGE_THROW TIMER_BOUNDARY MESSAGE_BOUNDARY ERROR_CATCH ERROR_BOUNDARY TERMINATE_END MESSAGE_END）' AFTER `node_config`,
    ADD COLUMN `event_name` varchar(100) NULL COMMENT '事件名称（消息事件使用）' AFTER `event_type`,
    ADD COLUMN `attached_to_node_code` varchar(100) NULL COMMENT '边界事件附着的任务节点编码' AFTER `event_name`,
    ADD COLUMN `interrupting` tinyint(1) NULL COMMENT '边界事件是否中断型' AFTER `attached_to_node_code`;

-- 修正 node_config 注释：自定义扩展属性实际在 node.ext（NodeServiceImpl.getExt 读 getExt()），node_config 是服务任务 / 事件等引擎节点配置容器。
ALTER TABLE `flow_node`
    MODIFY COLUMN `node_config` text NULL COMMENT '节点配置（服务任务、事件等引擎节点配置json）';

-- 修正状态列注释：v2.1.0 把状态拆成「实例状态 / 任务状态 / 协作方式」三个维度（映射见 StatusMapper），
-- 旧注释三表共用一套编码且含已移除的「2审批通过」，容易误读 —— 例如 8 在实例表是「已完成」、在任务表是「结束」。
-- 仅改注释，不动列类型与数据；存量库若不便执行这些重建语句，可直接跳过（不影响引擎行为）。
ALTER TABLE `flow_instance`
    MODIFY COLUMN `flow_status` varchar(20) NOT NULL COMMENT '实例状态（0待提交 1审批中 4终止 5作废 6撤销 7取回 8已完成 9已退回 12重启 13暂存）';
ALTER TABLE `flow_task`
    MODIFY COLUMN `flow_status` varchar(20) NOT NULL COMMENT '任务状态（1待办理 2通过 8结束 9退回 10失效 11拿回；任务结果，勿套用实例状态编码）';
ALTER TABLE `flow_his_task`
    MODIFY COLUMN `flow_status` varchar(20) NOT NULL COMMENT '任务状态（1待办理 2通过 8结束 9退回 10失效 11拿回；任务结果，勿套用实例状态编码）',
    MODIFY COLUMN `cooperate_type` tinyint(1) NOT NULL DEFAULT '0' COMMENT '协作方式（1无 2转办 3委派 4会签 5票签 6加签 7减签；行为维度，结果见 flow_status）';

-- 事件等待表：定时 / 消息等需要“等待”的事件统一落在此表，状态独立于 flow_status。
-- 用途：承载定时启动、中间定时捕获、中间消息捕获、定时边界、消息边界等待；一次性成功后删除，DEAD / CANCELLED 按保留期清理。
-- 索引：event_wait_status_next / event_wait_status_retry 支撑到期与重试扫描；event_wait_tenant_status_* 支撑多租户扫描；
--       event_wait_instance_task、event_wait_name_instance_status、event_wait_def_node_status 支撑实例 / 消息 / 定义维度的查询与取消。
-- 兼容：全新表，旧流程数据无记录；事件能力默认关闭，未开启时不扫描本表。
-- 回滚：DROP TABLE `flow_event_wait`；再 ALTER TABLE `flow_node` DROP COLUMN event_type, DROP COLUMN event_name,
--       DROP COLUMN attached_to_node_code, DROP COLUMN interrupting（回滚前须确认无事件定义引用这些列）。
CREATE TABLE `flow_event_wait`
(
    `id`               bigint       NOT NULL COMMENT '主键id',
    `tenant_id`        varchar(40)  DEFAULT NULL COMMENT '租户id',
    `del_flag`         char(1)      DEFAULT '0' COMMENT '删除标志',
    `definition_id`    bigint       DEFAULT NULL COMMENT '流程定义id（定时启动事件必须有）',
    `instance_id`      bigint       DEFAULT NULL COMMENT '流程实例id（实例等待事件必须有）',
    `node_code`        varchar(100) DEFAULT NULL COMMENT '事件节点编码（定位沿用 definition_id + node_code）',
    `event_type`       varchar(40)  NOT NULL COMMENT '事件类型（MESSAGE_START TIMER_START TIMER_CATCH MESSAGE_CATCH MESSAGE_THROW TIMER_BOUNDARY MESSAGE_BOUNDARY ERROR_CATCH ERROR_BOUNDARY TERMINATE_END MESSAGE_END）',
    `event_name`       varchar(100) DEFAULT NULL COMMENT '事件名称（消息事件使用）',
    `attached_task_id` bigint       DEFAULT NULL COMMENT '边界事件关联的待办任务id',
    `trigger_type`     varchar(40)  DEFAULT NULL COMMENT '触发策略类型（AFTER FIXED POINT_IN_TIME）',
    `trigger_interval` varchar(100) DEFAULT NULL COMMENT '触发策略原始配置',
    `next_trigger_at`  datetime     DEFAULT NULL COMMENT '下次触发时间',
    `timezone`         varchar(40)  DEFAULT NULL COMMENT '定时触发使用的时区',
    `repeat_count`     int          DEFAULT NULL COMMENT '已触发次数',
    `max_repeat_count` int          DEFAULT NULL COMMENT '最大触发次数',
    `status`           varchar(20)  NOT NULL COMMENT '等待状态（WAITING RUNNING CANCELLED RETRY DEAD）',
    `retry_count`      int          DEFAULT NULL COMMENT '已重试次数',
    `max_retry_count`  int          DEFAULT NULL COMMENT '最大重试次数',
    `retry_at`         datetime     DEFAULT NULL COMMENT '下次重试时间',
    `lock_owner`       varchar(64)  DEFAULT NULL COMMENT '抢占持有者标识',
    `lock_until`       datetime     DEFAULT NULL COMMENT '抢占租约到期时间',
    `version`          int          DEFAULT NULL COMMENT '乐观锁版本号',
    `payload`          text         COMMENT '消息事件接收的业务载荷',
    `last_error`       varchar(500) DEFAULT NULL COMMENT '最近一次失败原因摘要',
    `create_time`      datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time`      datetime     DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `event_wait_status_next` (`status`, `next_trigger_at`, `id`) USING BTREE,
    KEY `event_wait_status_retry` (`status`, `retry_at`, `id`) USING BTREE,
    KEY `event_wait_tenant_status_next` (`tenant_id`, `status`, `next_trigger_at`, `id`) USING BTREE,
    KEY `event_wait_tenant_status_retry` (`tenant_id`, `status`, `retry_at`, `id`) USING BTREE,
    KEY `event_wait_instance_task` (`instance_id`, `attached_task_id`) USING BTREE,
    KEY `event_wait_name_instance_status` (`event_name`, `instance_id`, `status`) USING BTREE,
    KEY `event_wait_def_node_status` (`definition_id`, `node_code`, `status`) USING BTREE
) ENGINE = InnoDB COMMENT ='事件等待表';
