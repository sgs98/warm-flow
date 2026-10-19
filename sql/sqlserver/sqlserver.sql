CREATE TABLE flow_definition (
    id bigint NOT NULL,
    flow_code nvarchar(40) NOT NULL,
    flow_name nvarchar(100) NOT NULL,
    model_value nvarchar(40) DEFAULT('CLASSICS') NOT NULL,
    category nvarchar(100) NULL,
    version nvarchar(20) NOT NULL,
    is_publish tinyint DEFAULT('0') NULL,
    form_custom nchar(1) DEFAULT('N') NULL,
    form_path nvarchar(100) NULL,
    activity_status tinyint DEFAULT('1') NULL,
    listener_type nvarchar(100) NULL,
    listener_path nvarchar(400) NULL,
    ext nvarchar(500) NULL,
    def_config nvarchar(max) NULL,
    create_time datetime2(7)  NULL,
    create_by nvarchar(64) NULL,
    update_time datetime2(7)  NULL,
    update_by nvarchar(64) NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    tenant_id nvarchar(40) NULL,
    CONSTRAINT PK__flow_def__3213E83FEE39AE33 PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'flow_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程名称',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'flow_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'设计器模型（CLASSICS经典模型 MIMIC仿钉钉模型）',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'model_value'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程类别',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'category'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程版本',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'version'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程版本',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'version'
GO

EXEC sp_addextendedproperty
'MS_Description', N'是否发布（0未发布 1已发布 9失效）',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'is_publish'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单是否自定义（Y是 N否）',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'form_custom'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单路径',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'form_path'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程激活状态（0挂起 1激活）',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'activity_status'
GO

EXEC sp_addextendedproperty
'MS_Description', N'监听器类型',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'listener_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'监听器路径',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'listener_path'
GO

EXEC sp_addextendedproperty
'MS_Description', N'业务详情 存业务表对象json字符串',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'ext'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程定义配置（自动审批等引擎配置与自定义扩展属性json）',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'def_config'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建人',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'create_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'update_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新人',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'update_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程定义表',
'SCHEMA', N'dbo',
'TABLE', N'flow_definition'
GO

CREATE TABLE flow_node (
    id bigint NOT NULL,
    node_type tinyint NOT NULL,
    definition_id bigint NOT NULL,
    node_code nvarchar(100) NOT NULL,
    node_name nvarchar(100) NULL,
    permission_flag nvarchar(200) NULL,
    node_ratio nvarchar(200)  NULL,
    coordinate nvarchar(100) NULL,
    any_node_skip nvarchar(100) NULL,
    listener_type nvarchar(100) NULL,
    listener_path nvarchar(400) NULL,
    form_custom nchar(1) DEFAULT('N') NULL,
    form_path nvarchar(100) NULL,
    create_time datetime2(7)  NULL,
    create_by nvarchar(64) NULL,
    update_time datetime2(7)  NULL,
    update_by nvarchar(64) NULL,
    ext nvarchar(max) NULL,
    node_config nvarchar(max) NULL,
    event_type nvarchar(40) NULL,
    event_name nvarchar(100) NULL,
    attached_to_node_code nvarchar(100) NULL,
    interrupting tinyint NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    tenant_id nvarchar(40) NULL,
    CONSTRAINT PK__flow_nod__3213E83F372470DE PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点类型（0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务）',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'node_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程定义id',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'definition_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程节点编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程节点名称',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'node_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'权限标识（权限类型:权限标识，可以多个，用@@隔开)',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'permission_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程签署比例值',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'node_ratio'
GO

EXEC sp_addextendedproperty
'MS_Description', N'坐标',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'coordinate'
GO

EXEC sp_addextendedproperty
'MS_Description', N'任意结点跳转',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'any_node_skip'
GO

EXEC sp_addextendedproperty
'MS_Description', N'监听器类型',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'listener_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'监听器路径',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'listener_path'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单是否自定义（Y是 N否）',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'form_custom'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单路径',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'form_path'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建人',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'create_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'update_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新人',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'update_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点扩展属性',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'ext'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点配置（服务任务、事件等引擎节点配置json）',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'node_config'
GO

EXEC sp_addextendedproperty
'MS_Description', N'事件类型（MESSAGE_START TIMER_START TIMER_CATCH MESSAGE_CATCH MESSAGE_THROW TIMER_BOUNDARY MESSAGE_BOUNDARY ERROR_CATCH ERROR_BOUNDARY TERMINATE_END MESSAGE_END）',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'event_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'事件名称（消息事件使用）',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'event_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'边界事件附着的任务节点编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'attached_to_node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'边界事件是否中断型',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'interrupting'
GO


EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_node',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程节点表',
'SCHEMA', N'dbo',
'TABLE', N'flow_node'
GO

CREATE TABLE flow_skip (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    now_node_code nvarchar(100) NOT NULL,
    now_node_type tinyint  NULL,
    next_node_code nvarchar(100) NOT NULL,
    next_node_type tinyint  NULL,
    skip_name nvarchar(100) NULL,
    skip_type nvarchar(40) NULL,
    skip_condition nvarchar(200) NULL,
    coordinate nvarchar(100) NULL,
    create_time datetime2(7)  NULL,
    create_by nvarchar(64) NULL,
    update_time datetime2(7)  NULL,
    update_by nvarchar(64) NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    tenant_id nvarchar(40) NULL,
    CONSTRAINT PK__flow_ski__3213E83F073FEE6E PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程定义id',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'definition_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'当前流程节点的编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'now_node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'当前节点类型（0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务）',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'now_node_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'下一个流程节点的编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'next_node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'下一个节点类型（0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务）',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'next_node_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'跳转名称',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'skip_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'跳转类型（PASS审批通过 REJECT退回）',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'skip_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'跳转条件',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'skip_condition'
GO

EXEC sp_addextendedproperty
'MS_Description', N'坐标',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'coordinate'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建人',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'create_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'update_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新人',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'update_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点跳转关联表',
'SCHEMA', N'dbo',
'TABLE', N'flow_skip'
GO

CREATE TABLE flow_instance (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    business_id nvarchar(40) NOT NULL,
    node_type tinyint NOT NULL,
    node_code nvarchar(100) NOT NULL,
    node_name nvarchar(100) NULL,
    variable nvarchar(max) NULL,
    flow_status nvarchar(20) NOT NULL,
    activity_status tinyint DEFAULT('1') NULL,
    def_json nvarchar(max) NULL,
    create_time datetime2(7)  NULL,
    create_by nvarchar(64) NULL,
    update_time datetime2(7)  NULL,
    update_by nvarchar(64) NULL,
    ext nvarchar(500) NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    tenant_id nvarchar(40) NULL,
    CONSTRAINT PK__flow_ins__3213E83F5190FEE1 PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
TEXTIMAGE_ON [PRIMARY]
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'对应flow_definition表的id',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'definition_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'业务id',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'business_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点类型（0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务）',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'node_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程节点编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程节点名称',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'node_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'任务变量',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'variable'
GO

EXEC sp_addextendedproperty
'MS_Description', N'实例状态（draft待提交 waiting审批中 termination终止 invalid作废 cancel撤销 finish已完成 back已退回 restart重启）',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'flow_status'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程激活状态（0挂起 1激活）',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'activity_status'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程定义json',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'def_json'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建人',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'create_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'update_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新人',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'update_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'扩展字段，预留给业务系统使用',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'ext'
GO

EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程实例表',
'SCHEMA', N'dbo',
'TABLE', N'flow_instance'
GO

CREATE TABLE flow_task (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    instance_id bigint NOT NULL,
    node_code nvarchar(100) NOT NULL,
    node_name nvarchar(100) NULL,
    node_type tinyint NOT NULL,
    flow_status nvarchar(20) NOT NULL,
    form_custom nchar(1) DEFAULT('N') NULL,
    form_path nvarchar(100) NULL,
    create_time datetime2(7)  NULL,
    create_by nvarchar(64) NULL,
    update_time datetime2(7)  NULL,
    update_by nvarchar(64) NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    tenant_id nvarchar(40) NULL,
    CONSTRAINT PK__flow_tas__3213E83F5AE1F1BA PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'对应flow_definition表的id',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'definition_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'对应flow_instance表的id',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'instance_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点名称',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'node_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点类型（0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务）',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'node_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'任务状态（pending待审批 pass通过 auto_pass自动审批 back退回 invalid失效 take_back拿回 copy抄送；任务结果，勿套用实例状态编码）',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'flow_status'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单是否自定义（Y是 N否）',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'form_custom'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单路径',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'form_path'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建人',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'create_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'update_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新人',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'update_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_task',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'待办任务表',
'SCHEMA', N'dbo',
'TABLE', N'flow_task'
GO

CREATE TABLE flow_his_task (
    id bigint NOT NULL,
    definition_id bigint NOT NULL,
    instance_id bigint NOT NULL,
    task_id bigint NOT NULL,
    node_code nvarchar(200) NULL,
    node_name nvarchar(200) NULL,
    node_type tinyint  NULL,
    target_node_code nvarchar(100) NULL,
    target_node_name nvarchar(100) NULL,
    approver nvarchar(40) NULL,
    cooperate_type varchar(20) DEFAULT('none') NULL,
    collaborator nvarchar(500) NULL,
    skip_type nvarchar(10) NOT NULL,
    flow_status nvarchar(20) NOT NULL,
    form_custom nchar(1) DEFAULT('N') NULL,
    form_path nvarchar(100) NULL,
    message nvarchar(500) NULL,
    variable nvarchar(max) NULL,
    ext nvarchar(max) NULL,
    node_config nvarchar(max) NULL,
    create_time datetime2(7)  NULL,
    update_time datetime2(7)  NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    tenant_id nvarchar(40) NULL,
    CONSTRAINT PK__flow_his__3213E83F67951564 PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'对应flow_definition表的id',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'definition_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'对应flow_instance表的id',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'instance_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'对应flow_task表的id',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'task_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'开始节点编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'开始节点名称',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'node_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'开始节点类型（0开始节点 1中间节点 2结束节点 3互斥网关 4并行网关 5包容网关 6服务任务）',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'node_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'目标节点编码',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'target_node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'结束节点名称',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'target_node_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批者',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'approver'
GO

EXEC sp_addextendedproperty
'MS_Description', N'协作方式（none无 transfer转办 delegate委派 add_signer加签 remove_signer减签；行为维度，抄送/会签/票签不落此列，结果见 flow_status）',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'cooperate_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'协作人',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'collaborator'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流转类型（PASS通过 REJECT退回 NONE无动作）',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'skip_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'任务状态（pending待审批 pass通过 auto_pass自动审批 back退回 invalid失效 take_back拿回 copy抄送；任务结果，勿套用实例状态编码）',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'flow_status'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单是否自定义（Y是 N否）',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'form_custom'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批表单路径',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'form_path'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批意见',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'message'
GO

EXEC sp_addextendedproperty
'MS_Description', N'任务变量',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'variable'
GO

EXEC sp_addextendedproperty
'MS_Description', N'业务详情 存业务表对象json字符串',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'ext'
GO

EXEC sp_addextendedproperty
'MS_Description', N'节点配置（服务任务等引擎配置与自定义扩展属性json）',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'node_config'
GO

EXEC sp_addextendedproperty
'MS_Description', N'任务开始时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'审批完成时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'update_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'历史任务记录表',
'SCHEMA', N'dbo',
'TABLE', N'flow_his_task'
GO

CREATE TABLE flow_user (
    id bigint NOT NULL,
    type nchar(1) NOT NULL,
    processed_by nvarchar(80) NULL,
    associated bigint NOT NULL,
    create_time datetime2(7)  NULL,
    create_by nvarchar(64) NULL,
    update_time datetime2(7)  NULL,
    update_by nvarchar(64) NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    tenant_id nvarchar(40) NULL,
    CONSTRAINT PK__flow_use__3213E83FFA38CA8B PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX user_processed_type ON flow_user (processed_by ASC, type ASC)
GO
CREATE NONCLUSTERED INDEX user_associated_idx ON flow_user (associated ASC)
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'人员类型（1待办任务的审批人权限 2待办任务的转办人权限 3待办任务的委托人权限 4待办任务的抄送人）',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'权限人',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'processed_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'关联id（待办任务id；抄送人记录为 flow_his_task.id）',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'associated'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建人',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'create_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'update_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新人',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'update_by'
GO

EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_user',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程用户表',
'SCHEMA', N'dbo',
'TABLE', N'flow_user'
GO

-- 事件等待表：定时 / 消息等需要“等待”的事件统一落在此表，状态独立于流程状态。
CREATE TABLE flow_event_wait (
    id bigint NOT NULL,
    tenant_id nvarchar(40) NULL,
    del_flag nchar(1) DEFAULT('0') NULL,
    definition_id bigint NULL,
    instance_id bigint NULL,
    node_code nvarchar(100) NULL,
    event_type nvarchar(40) NOT NULL,
    event_name nvarchar(100) NULL,
    attached_task_id bigint NULL,
    trigger_type nvarchar(40) NULL,
    trigger_interval nvarchar(100) NULL,
    next_trigger_at datetime2(7) NULL,
    timezone nvarchar(40) NULL,
    repeat_count int NULL,
    max_repeat_count int NULL,
    status nvarchar(20) NOT NULL,
    retry_count int NULL,
    max_retry_count int NULL,
    retry_at datetime2(7) NULL,
    lock_owner nvarchar(64) NULL,
    lock_until datetime2(7) NULL,
    version int NULL,
    payload nvarchar(max) NULL,
    last_error nvarchar(500) NULL,
    create_time datetime2(7) NULL,
    update_time datetime2(7) NULL,
    CONSTRAINT PK_flow_event_wait PRIMARY KEY CLUSTERED (id)
    WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON)
    ON [PRIMARY]
)
ON [PRIMARY]
GO

CREATE NONCLUSTERED INDEX event_wait_status_next ON flow_event_wait (status ASC, next_trigger_at ASC, id ASC)
GO
CREATE NONCLUSTERED INDEX event_wait_status_retry ON flow_event_wait (status ASC, retry_at ASC, id ASC)
GO
CREATE NONCLUSTERED INDEX event_wait_tenant_status_next ON flow_event_wait (tenant_id ASC, status ASC, next_trigger_at ASC, id ASC)
GO
CREATE NONCLUSTERED INDEX event_wait_tenant_status_retry ON flow_event_wait (tenant_id ASC, status ASC, retry_at ASC, id ASC)
GO
CREATE NONCLUSTERED INDEX event_wait_instance_task ON flow_event_wait (instance_id ASC, attached_task_id ASC)
GO
CREATE NONCLUSTERED INDEX event_wait_name_instance_status ON flow_event_wait (event_name ASC, instance_id ASC, status ASC)
GO
CREATE NONCLUSTERED INDEX event_wait_def_node_status ON flow_event_wait (definition_id ASC, node_code ASC, status ASC)
GO

EXEC sp_addextendedproperty
'MS_Description', N'事件等待表',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait'
GO

EXEC sp_addextendedproperty
'MS_Description', N'主键id',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'租户id',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'tenant_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'删除标志',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'del_flag'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程定义id（定时启动事件必须有）',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'definition_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'流程实例id（实例等待事件必须有）',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'instance_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'事件节点编码（定位沿用 definition_id + node_code）',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'node_code'
GO

EXEC sp_addextendedproperty
'MS_Description', N'事件类型（MESSAGE_START TIMER_START TIMER_CATCH MESSAGE_CATCH MESSAGE_THROW TIMER_BOUNDARY MESSAGE_BOUNDARY ERROR_CATCH ERROR_BOUNDARY TERMINATE_END MESSAGE_END）',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'event_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'事件名称（消息事件使用）',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'event_name'
GO

EXEC sp_addextendedproperty
'MS_Description', N'边界事件关联的待办任务id',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'attached_task_id'
GO

EXEC sp_addextendedproperty
'MS_Description', N'触发策略类型（AFTER FIXED POINT_IN_TIME）',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'trigger_type'
GO

EXEC sp_addextendedproperty
'MS_Description', N'触发策略原始配置',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'trigger_interval'
GO

EXEC sp_addextendedproperty
'MS_Description', N'下次触发时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'next_trigger_at'
GO

EXEC sp_addextendedproperty
'MS_Description', N'定时触发使用的时区',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'timezone'
GO

EXEC sp_addextendedproperty
'MS_Description', N'已触发次数',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'repeat_count'
GO

EXEC sp_addextendedproperty
'MS_Description', N'最大触发次数',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'max_repeat_count'
GO

EXEC sp_addextendedproperty
'MS_Description', N'等待状态（WAITING RUNNING CANCELLED RETRY DEAD）',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'status'
GO

EXEC sp_addextendedproperty
'MS_Description', N'已重试次数',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'retry_count'
GO

EXEC sp_addextendedproperty
'MS_Description', N'最大重试次数',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'max_retry_count'
GO

EXEC sp_addextendedproperty
'MS_Description', N'下次重试时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'retry_at'
GO

EXEC sp_addextendedproperty
'MS_Description', N'抢占持有者标识',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'lock_owner'
GO

EXEC sp_addextendedproperty
'MS_Description', N'抢占租约到期时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'lock_until'
GO

EXEC sp_addextendedproperty
'MS_Description', N'乐观锁版本号',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'version'
GO

EXEC sp_addextendedproperty
'MS_Description', N'消息事件接收的业务载荷',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'payload'
GO

EXEC sp_addextendedproperty
'MS_Description', N'最近一次失败原因摘要',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'last_error'
GO

EXEC sp_addextendedproperty
'MS_Description', N'创建时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'create_time'
GO

EXEC sp_addextendedproperty
'MS_Description', N'更新时间',
'SCHEMA', N'dbo',
'TABLE', N'flow_event_wait',
'COLUMN', N'update_time'
GO
