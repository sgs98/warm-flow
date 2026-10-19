create table FLOW_DEFINITION
(
    ID              NUMBER(20)            not null,
    FLOW_CODE       VARCHAR2(40)          not null,
    FLOW_NAME       VARCHAR2(100)         not null,
    MODEL_VALUE            VARCHAR2(40) default 'CLASSICS' not null,
    CATEGORY        VARCHAR2(100),
    VERSION         VARCHAR2(20)          not null,
    IS_PUBLISH      NUMBER(1)   default 0 not null,
    FORM_CUSTOM     VARCHAR2(1) default 'N',
    FORM_PATH       VARCHAR2(100),
    ACTIVITY_STATUS NUMBER(1)   default 1,
    LISTENER_TYPE   VARCHAR2(100),
    LISTENER_PATH   VARCHAR2(500),
    EXT             VARCHAR2(500),
    DEF_CONFIG     CLOB,
    CREATE_TIME     DATE,
    CREATE_BY       VARCHAR2(64) default '',
    UPDATE_TIME     DATE,
    UPDATE_BY       VARCHAR2(64) default '',
    DEL_FLAG        VARCHAR2(1) default '0',
    TENANT_ID       VARCHAR2(40)
);

alter table FLOW_DEFINITION
    add constraint PK_FLOW_DEFINITION primary key (ID);

comment on table FLOW_DEFINITION is '流程定义表';
comment on column FLOW_DEFINITION.ID is '主键id';
comment on column FLOW_DEFINITION.FLOW_CODE is '流程编码';
comment on column FLOW_DEFINITION.FLOW_NAME is '流程名称';
comment on column FLOW_DEFINITION.MODEL_VALUE is '设计器模型（CLASSICS经典模型 MIMIC仿钉钉模型）';
comment on column FLOW_DEFINITION.CATEGORY is '流程类别';
comment on column FLOW_DEFINITION.VERSION is '流程版本';
comment on column FLOW_DEFINITION.IS_PUBLISH is '是否发布 (0未发布 1已发布 9失效)';
comment on column FLOW_DEFINITION.FORM_CUSTOM is '审批表单是否自定义 (Y是 N否)';
comment on column FLOW_DEFINITION.FORM_PATH is '审批表单路径';
comment on column FLOW_DEFINITION.ACTIVITY_STATUS is '流程激活状态（0挂起 1激活）';
comment on column FLOW_DEFINITION.LISTENER_TYPE is '监听器类型';
comment on column FLOW_DEFINITION.LISTENER_PATH is '监听器路径';
comment on column FLOW_DEFINITION.EXT is '扩展字段，预留给业务系统使用';
comment on column FLOW_DEFINITION.DEF_CONFIG is '流程定义配置（自动审批等引擎配置与自定义扩展属性json）';
comment on column FLOW_DEFINITION.CREATE_TIME is '创建时间';
comment on column FLOW_DEFINITION.CREATE_BY is '创建人';
comment on column FLOW_DEFINITION.UPDATE_TIME is '更新时间';
comment on column FLOW_DEFINITION.UPDATE_BY is '更新人';
comment on column FLOW_DEFINITION.DEL_FLAG is '删除标志';
comment on column FLOW_DEFINITION.TENANT_ID is '租户id';

create table FLOW_NODE
(
    ID              NUMBER(20)    not null,
    NODE_TYPE       NUMBER(1)     not null,
    DEFINITION_ID   NUMBER(20)    not null,
    NODE_CODE       VARCHAR2(100) not null,
    NODE_NAME       VARCHAR2(100),
    PERMISSION_FLAG VARCHAR2(200),
    NODE_RATIO      VARCHAR2(200),
    COORDINATE      VARCHAR2(100),
    ANY_NODE_SKIP   VARCHAR2(100),
    LISTENER_TYPE   VARCHAR2(100),
    LISTENER_PATH   VARCHAR2(500),
    FORM_CUSTOM     VARCHAR2(1)   default 'N',
    FORM_PATH       VARCHAR2(100),
    CREATE_TIME     DATE,
    CREATE_BY       VARCHAR2(64) default '',
    UPDATE_TIME     DATE,
    UPDATE_BY       VARCHAR2(64) default '',
    EXT        CLOB,
    NODE_CONFIG CLOB,
    EVENT_TYPE          VARCHAR2(40),
    EVENT_NAME          VARCHAR2(100),
    ATTACHED_TO_NODE_CODE VARCHAR2(100),
    INTERRUPTING        NUMBER(1),
    DEL_FLAG        VARCHAR2(1)   default '0',
    TENANT_ID       VARCHAR2(40)
);

alter table FLOW_NODE
    add constraint PK_FLOW_NODE primary key (ID);

comment on table FLOW_NODE is '流程节点表';
comment on column FLOW_NODE.ID is '主键id';
comment on column FLOW_NODE.NODE_TYPE is '节点类型（0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关）';
comment on column FLOW_NODE.DEFINITION_ID is '对应flow_definition表的id';
comment on column FLOW_NODE.NODE_CODE is '流程节点编码';
comment on column FLOW_NODE.NODE_NAME is '流程节点名称';
comment on column FLOW_NODE.NODE_RATIO is '流程签署比例值';
comment on column FLOW_NODE.COORDINATE is '坐标';
comment on column FLOW_NODE.ANY_NODE_SKIP is '任意结点跳转';
comment on column FLOW_NODE.LISTENER_TYPE is '监听器类型';
comment on column FLOW_NODE.LISTENER_PATH is '监听器路径';
comment on column FLOW_NODE.FORM_CUSTOM is '审批表单是否自定义 (Y是 N否)';
comment on column FLOW_NODE.FORM_PATH is '审批表单路径';
comment on column FLOW_NODE.CREATE_TIME is '创建时间';
comment on column FLOW_NODE.CREATE_BY is '创建人';
comment on column FLOW_NODE.UPDATE_TIME is '更新时间';
comment on column FLOW_NODE.UPDATE_BY is '更新人';
comment on column FLOW_NODE.EXT is '节点扩展属性';
comment on column FLOW_NODE.NODE_CONFIG is '节点配置（服务任务、事件等引擎节点配置json）';
comment on column FLOW_NODE.EVENT_TYPE is '事件类型（MESSAGE_START TIMER_START TIMER_CATCH MESSAGE_CATCH MESSAGE_THROW TIMER_BOUNDARY MESSAGE_BOUNDARY ERROR_CATCH ERROR_BOUNDARY TERMINATE_END MESSAGE_END）';
comment on column FLOW_NODE.EVENT_NAME is '事件名称（消息事件使用）';
comment on column FLOW_NODE.ATTACHED_TO_NODE_CODE is '边界事件附着的任务节点编码';
comment on column FLOW_NODE.INTERRUPTING is '边界事件是否中断型';
comment on column FLOW_NODE.DEL_FLAG is '删除标志';
comment on column FLOW_NODE.TENANT_ID is '租户id';
comment on column FLOW_NODE.PERMISSION_FLAG is '权限标识（权限类型:权限标识，可以多个，用@@隔开)';

create table FLOW_SKIP
(
    ID             NUMBER(20)    not null,
    DEFINITION_ID  NUMBER(20)    not null,
    NOW_NODE_CODE  VARCHAR2(100) not null,
    NOW_NODE_TYPE  NUMBER(1),
    NEXT_NODE_CODE VARCHAR2(100) not null,
    NEXT_NODE_TYPE NUMBER(1),
    SKIP_NAME      VARCHAR2(100),
    SKIP_TYPE      VARCHAR2(40),
    SKIP_CONDITION VARCHAR2(200),
    COORDINATE     VARCHAR2(100),
    CREATE_TIME    DATE,
    CREATE_BY      VARCHAR2(64) default '',
    UPDATE_TIME    DATE,
    UPDATE_BY      VARCHAR2(64) default '',
    DEL_FLAG       VARCHAR2(1) default '0',
    TENANT_ID      VARCHAR2(40)
);

alter table FLOW_SKIP
    add constraint PK_FLOW_SKIP primary key (ID);

comment on table FLOW_SKIP is '节点跳转关联表';
comment on column FLOW_SKIP.ID is '主键id';
comment on column FLOW_SKIP.DEFINITION_ID is '流程定义id';
comment on column FLOW_SKIP.NOW_NODE_CODE is '当前流程节点类型 (0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务)';
comment on column FLOW_SKIP.NOW_NODE_TYPE is '下一个流程节点类型 (0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务)';
comment on column FLOW_SKIP.NEXT_NODE_CODE is '下一个流程节点编码';
comment on column FLOW_SKIP.NEXT_NODE_TYPE is '下一个流程节点类型 (0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务)';
comment on column FLOW_SKIP.SKIP_NAME is '跳转名称';
comment on column FLOW_SKIP.SKIP_TYPE is '跳转类型 (PASS审批通过 REJECT退回)';
comment on column FLOW_SKIP.SKIP_CONDITION is '跳转条件';
comment on column FLOW_SKIP.COORDINATE is '坐标';
comment on column FLOW_SKIP.CREATE_TIME is '创建时间';
comment on column FLOW_SKIP.CREATE_BY is '创建人';
comment on column FLOW_SKIP.UPDATE_TIME is '更新时间';
comment on column FLOW_SKIP.UPDATE_BY is '更新人';
comment on column FLOW_SKIP.DEL_FLAG is '删除标志';
comment on column FLOW_SKIP.TENANT_ID is '租户id';

create table FLOW_INSTANCE
(
    ID              NUMBER       not null,
    DEFINITION_ID   NUMBER       not null,
    BUSINESS_ID     VARCHAR2(40) not null,
    NODE_TYPE       NUMBER(1),
    NODE_CODE       VARCHAR2(100),
    NODE_NAME       VARCHAR2(100),
    VARIABLE        CLOB,
    FLOW_STATUS     VARCHAR2(20),
    ACTIVITY_STATUS NUMBER(1)    default 1,
    DEF_JSON        CLOB,
    CREATE_TIME     DATE,
    CREATE_BY       VARCHAR2(64) default '',
    UPDATE_TIME     DATE,
    UPDATE_BY       VARCHAR2(64) default '',
    EXT             VARCHAR2(500),
    DEL_FLAG        VARCHAR2(1)  default '0',
    TENANT_ID       VARCHAR2(40)
);

alter table FLOW_INSTANCE
    add constraint PK_FLOW_INSTANCE primary key (ID);

comment on table FLOW_INSTANCE is '流程实例表';
comment on column FLOW_INSTANCE.ID is '主键id';
comment on column FLOW_INSTANCE.DEFINITION_ID is '对应flow_definition表的id';
comment on column FLOW_INSTANCE.BUSINESS_ID is '业务id';
comment on column FLOW_INSTANCE.NODE_TYPE is '开始节点类型 (0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务)';
comment on column FLOW_INSTANCE.NODE_CODE is '开始节点编码';
comment on column FLOW_INSTANCE.NODE_NAME is '开始节点名称';
comment on column FLOW_INSTANCE.VARIABLE is '任务变量';
comment on column FLOW_INSTANCE.FLOW_STATUS is '实例状态（draft待提交 waiting审批中 termination终止 invalid作废 cancel撤销 finish已完成 back已退回 restart重启）';
comment on column FLOW_INSTANCE.ACTIVITY_STATUS is '流程激活状态（0挂起 1激活）';
comment on column FLOW_INSTANCE.DEF_JSON is '流程定义json';
comment on column FLOW_INSTANCE.CREATE_TIME is '创建时间';
comment on column FLOW_INSTANCE.CREATE_BY is '创建人';
comment on column FLOW_INSTANCE.UPDATE_TIME is '更新时间';
comment on column FLOW_INSTANCE.UPDATE_BY is '更新人';
comment on column FLOW_INSTANCE.EXT is '扩展字段，预留给业务系统使用';
comment on column FLOW_INSTANCE.DEL_FLAG is '删除标志';
comment on column FLOW_INSTANCE.TENANT_ID is '租户id';

create table FLOW_TASK
(
    ID            NUMBER(20) not null,
    DEFINITION_ID NUMBER(20) not null,
    INSTANCE_ID   NUMBER(20) not null,
    NODE_CODE     VARCHAR2(100),
    NODE_NAME     VARCHAR2(100),
    NODE_TYPE     NUMBER(1),
    FLOW_STATUS   VARCHAR2(20),
    FORM_CUSTOM   VARCHAR2(1) default 'N',
    FORM_PATH     VARCHAR2(100),
    CREATE_TIME   DATE,
    CREATE_BY     VARCHAR2(64) default '',
    UPDATE_TIME   DATE,
    UPDATE_BY     VARCHAR2(64) default '',
    DEL_FLAG      VARCHAR2(1) default '0',
    TENANT_ID     VARCHAR2(40)
);

alter table FLOW_TASK
    add constraint PK_FLOW_TASK primary key (ID);

comment on table FLOW_TASK is '待办任务表';
comment on column FLOW_TASK.ID is '主键id';
comment on column FLOW_TASK.DEFINITION_ID is '对应flow_definition表的id';
comment on column FLOW_TASK.INSTANCE_ID is '对应flow_instance表的id';
comment on column FLOW_TASK.NODE_CODE is '节点编码';
comment on column FLOW_TASK.NODE_NAME is '节点名称';
comment on column FLOW_TASK.NODE_TYPE is '节点类型 (0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务)';
comment on column FLOW_TASK.FLOW_STATUS is '任务状态（pending待审批 pass通过 auto_pass自动审批 back退回 invalid失效 take_back拿回 copy抄送；任务结果，勿套用实例状态编码）';
comment on column FLOW_TASK.FORM_CUSTOM is '审批表单是否自定义 (Y是 N否)';
comment on column FLOW_TASK.FORM_PATH is '审批表单路径';
comment on column FLOW_TASK.CREATE_TIME is '创建时间';
comment on column FLOW_TASK.CREATE_BY is '创建人';
comment on column FLOW_TASK.UPDATE_TIME is '更新时间';
comment on column FLOW_TASK.UPDATE_BY is '更新人';
comment on column FLOW_TASK.DEL_FLAG is '删除标志';
comment on column FLOW_TASK.TENANT_ID is '租户id';

create table FLOW_HIS_TASK
(
    ID               NUMBER(20) not null,
    DEFINITION_ID    NUMBER(20) not null,
    INSTANCE_ID      NUMBER(20) not null,
    TASK_ID          NUMBER(20) not null,
    NODE_CODE        VARCHAR2(100),
    NODE_NAME        VARCHAR2(100),
    NODE_TYPE        NUMBER(1),
    TARGET_NODE_CODE VARCHAR2(200),
    TARGET_NODE_NAME VARCHAR2(200),
    APPROVER         VARCHAR2(40),
    COOPERATE_TYPE   VARCHAR2(20) default 'none',
    COLLABORATOR     VARCHAR2(500),
    SKIP_TYPE        VARCHAR2(10),
    FLOW_STATUS      VARCHAR2(20),
    FORM_CUSTOM      VARCHAR2(1) default 'N',
    FORM_PATH        VARCHAR2(100),
    MESSAGE          VARCHAR2(500),
    VARIABLE         CLOB,
    EXT              CLOB,
    NODE_CONFIG      CLOB,
    CREATE_TIME      DATE,
    UPDATE_TIME      DATE,
    DEL_FLAG         VARCHAR2(1) default '0',
    TENANT_ID        VARCHAR2(40)

);

alter table FLOW_HIS_TASK
    add constraint PK_FLOW_HIS_TASK primary key (ID);

comment on table FLOW_HIS_TASK is '历史任务记录表';
comment on column FLOW_HIS_TASK.ID is '主键id';
comment on column FLOW_HIS_TASK.DEFINITION_ID is '对应flow_definition表的id';
comment on column FLOW_HIS_TASK.INSTANCE_ID is '对应flow_instance表的id';
comment on column FLOW_HIS_TASK.TASK_ID is '对应flow_task表的id';
comment on column FLOW_HIS_TASK.NODE_CODE is '开始节点编码';
comment on column FLOW_HIS_TASK.NODE_NAME is '开始节点名称';
comment on column FLOW_HIS_TASK.NODE_TYPE is '开始节点类型 (0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务)';
comment on column FLOW_HIS_TASK.TARGET_NODE_CODE is '目标节点编码';
comment on column FLOW_HIS_TASK.TARGET_NODE_NAME is '目标节点名称';
comment on column FLOW_HIS_TASK.SKIP_TYPE is '流转类型（PASS通过 REJECT退回 NONE无动作）';
comment on column FLOW_HIS_TASK.FLOW_STATUS is '任务状态（pending待审批 pass通过 auto_pass自动审批 back退回 invalid失效 take_back拿回 copy抄送；任务结果，勿套用实例状态编码）';
comment on column FLOW_HIS_TASK.FORM_CUSTOM is '审批表单是否自定义 (Y是 N否)';
comment on column FLOW_HIS_TASK.FORM_PATH is '审批表单路径';
comment on column FLOW_HIS_TASK.MESSAGE is '审批意见';
comment on column FLOW_HIS_TASK.VARIABLE is '任务变量';
comment on column FLOW_HIS_TASK.EXT is '扩展字段，预留给业务系统使用';
comment on column FLOW_HIS_TASK.NODE_CONFIG is '节点配置（服务任务等引擎配置与自定义扩展属性json）';
comment on column FLOW_HIS_TASK.CREATE_TIME is '任务开始时间';
comment on column FLOW_HIS_TASK.UPDATE_TIME is '审批完成时间';
comment on column FLOW_HIS_TASK.DEL_FLAG is '删除标志';
comment on column FLOW_HIS_TASK.TENANT_ID is '租户id';
comment on column FLOW_HIS_TASK.APPROVER is '审批者';
comment on column FLOW_HIS_TASK.COOPERATE_TYPE is '协作方式（none无 transfer转办 delegate委派 add_signer加签 remove_signer减签；行为维度，抄送/会签/票签不落此列，结果见 flow_status）';
comment on column FLOW_HIS_TASK.COLLABORATOR is '协作人';

create table FLOW_USER
(
    ID           NUMBER(20)  not null,
    TYPE         VARCHAR2(1) not null,
    PROCESSED_BY VARCHAR2(80),
    ASSOCIATED   NUMBER(20)  not null,
    CREATE_TIME  DATE,
    CREATE_BY    VARCHAR2(64) default '',
    UPDATE_TIME  DATE,
    UPDATE_BY    VARCHAR2(64) default '',
    DEL_FLAG     VARCHAR2(1) default '0',
    TENANT_ID    VARCHAR2(40)
);

alter table FLOW_USER
    add constraint PK_FLOW_USER primary key (ID);

comment on table FLOW_USER is '待办任务表';
comment on column FLOW_USER.ID is '主键id';
comment on column FLOW_USER.TYPE is '人员类型（1待办任务的审批人权限 2待办任务的转办人权限 3待办任务的委托人权限 4待办任务的抄送人）';
comment on column FLOW_USER.PROCESSED_BY is '权限人)';
comment on column FLOW_USER.ASSOCIATED is '关联id（待办任务id；抄送人记录为 flow_his_task.id）';
comment on column FLOW_USER.CREATE_TIME is '创建时间';
comment on column FLOW_USER.CREATE_BY is '创建人';
comment on column FLOW_USER.UPDATE_TIME is '更新时间';
comment on column FLOW_USER.UPDATE_BY is '更新人';
comment on column FLOW_USER.DEL_FLAG is '删除标志';
comment on column FLOW_USER.TENANT_ID is '租户id';

create index USER_PROCESSED_TYPE on FLOW_USER (PROCESSED_BY, TYPE);
create index USER_ASSOCIATED_IDX on FLOW_USER (ASSOCIATED);

-- 事件等待表：定时 / 消息等需要“等待”的事件统一落在此表，状态独立于流程状态。
create table FLOW_EVENT_WAIT
(
    ID                NUMBER(20)    not null,
    TENANT_ID         VARCHAR2(40),
    DEL_FLAG          VARCHAR2(1) default '0',
    DEFINITION_ID     NUMBER(20),
    INSTANCE_ID       NUMBER(20),
    NODE_CODE         VARCHAR2(100),
    EVENT_TYPE        VARCHAR2(40)  not null,
    EVENT_NAME        VARCHAR2(100),
    ATTACHED_TASK_ID  NUMBER(20),
    TRIGGER_TYPE      VARCHAR2(40),
    TRIGGER_INTERVAL  VARCHAR2(100),
    NEXT_TRIGGER_AT   DATE,
    TIMEZONE          VARCHAR2(40),
    REPEAT_COUNT      NUMBER(10),
    MAX_REPEAT_COUNT  NUMBER(10),
    STATUS            VARCHAR2(20)  not null,
    RETRY_COUNT       NUMBER(10),
    MAX_RETRY_COUNT   NUMBER(10),
    RETRY_AT          DATE,
    LOCK_OWNER        VARCHAR2(64),
    LOCK_UNTIL        DATE,
    VERSION           NUMBER(10),
    PAYLOAD           CLOB,
    LAST_ERROR        VARCHAR2(500),
    CREATE_TIME       DATE,
    UPDATE_TIME       DATE
);

alter table FLOW_EVENT_WAIT
    add constraint PK_FLOW_EVENT_WAIT primary key (ID);

comment on table FLOW_EVENT_WAIT is '事件等待表';
comment on column FLOW_EVENT_WAIT.ID is '主键id';
comment on column FLOW_EVENT_WAIT.TENANT_ID is '租户id';
comment on column FLOW_EVENT_WAIT.DEL_FLAG is '删除标志';
comment on column FLOW_EVENT_WAIT.DEFINITION_ID is '流程定义id（定时启动事件必须有）';
comment on column FLOW_EVENT_WAIT.INSTANCE_ID is '流程实例id（实例等待事件必须有）';
comment on column FLOW_EVENT_WAIT.NODE_CODE is '事件节点编码（定位沿用 definition_id + node_code）';
comment on column FLOW_EVENT_WAIT.EVENT_TYPE is '事件类型（MESSAGE_START TIMER_START TIMER_CATCH MESSAGE_CATCH MESSAGE_THROW TIMER_BOUNDARY MESSAGE_BOUNDARY ERROR_CATCH ERROR_BOUNDARY TERMINATE_END MESSAGE_END）';
comment on column FLOW_EVENT_WAIT.EVENT_NAME is '事件名称（消息事件使用）';
comment on column FLOW_EVENT_WAIT.ATTACHED_TASK_ID is '边界事件关联的待办任务id';
comment on column FLOW_EVENT_WAIT.TRIGGER_TYPE is '触发策略类型（AFTER FIXED POINT_IN_TIME）';
comment on column FLOW_EVENT_WAIT.TRIGGER_INTERVAL is '触发策略原始配置';
comment on column FLOW_EVENT_WAIT.NEXT_TRIGGER_AT is '下次触发时间';
comment on column FLOW_EVENT_WAIT.TIMEZONE is '定时触发使用的时区';
comment on column FLOW_EVENT_WAIT.REPEAT_COUNT is '已触发次数';
comment on column FLOW_EVENT_WAIT.MAX_REPEAT_COUNT is '最大触发次数';
comment on column FLOW_EVENT_WAIT.STATUS is '等待状态（WAITING RUNNING CANCELLED RETRY DEAD）';
comment on column FLOW_EVENT_WAIT.RETRY_COUNT is '已重试次数';
comment on column FLOW_EVENT_WAIT.MAX_RETRY_COUNT is '最大重试次数';
comment on column FLOW_EVENT_WAIT.RETRY_AT is '下次重试时间';
comment on column FLOW_EVENT_WAIT.LOCK_OWNER is '抢占持有者标识';
comment on column FLOW_EVENT_WAIT.LOCK_UNTIL is '抢占租约到期时间';
comment on column FLOW_EVENT_WAIT.VERSION is '乐观锁版本号';
comment on column FLOW_EVENT_WAIT.PAYLOAD is '消息事件接收的业务载荷';
comment on column FLOW_EVENT_WAIT.LAST_ERROR is '最近一次失败原因摘要';
comment on column FLOW_EVENT_WAIT.CREATE_TIME is '创建时间';
comment on column FLOW_EVENT_WAIT.UPDATE_TIME is '更新时间';

create index EVENT_WAIT_STATUS_NEXT on FLOW_EVENT_WAIT (STATUS, NEXT_TRIGGER_AT, ID);
create index EVENT_WAIT_STATUS_RETRY on FLOW_EVENT_WAIT (STATUS, RETRY_AT, ID);
create index EVENT_WAIT_TENANT_NEXT on FLOW_EVENT_WAIT (TENANT_ID, STATUS, NEXT_TRIGGER_AT, ID);
create index EVENT_WAIT_TENANT_RETRY on FLOW_EVENT_WAIT (TENANT_ID, STATUS, RETRY_AT, ID);
create index EVENT_WAIT_INSTANCE_TASK on FLOW_EVENT_WAIT (INSTANCE_ID, ATTACHED_TASK_ID);
create index EVENT_WAIT_NAME_INST_STATUS on FLOW_EVENT_WAIT (EVENT_NAME, INSTANCE_ID, STATUS);
create index EVENT_WAIT_DEF_NODE_STATUS on FLOW_EVENT_WAIT (DEFINITION_ID, NODE_CODE, STATUS);
