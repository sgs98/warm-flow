/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.core.constant;

/**
 * 工作流异常提示常量。
 * <p>
 * 这里集中维护 core 服务、流程图校验、状态机、表单、协作办理等公共异常消息。
 * 常量值会暴露给下游调用方，调整文案时需要考虑兼容调用方断言或国际化适配。
 *
 * @author warm
 * @since 2023/3/30 14:05
 */
public class ExceptionCons {

    /**
     * 中间节点存在相同跳转类型且目标节点相同的连线。
     */
    public static final String SAME_CONDITION_VALUE = "中间节点，同一个节点不能有相同跳转类型，跳转同一个目标节点!";

    /**
     * 互斥网关存在相同跳转条件且目标节点相同的连线。
     */
    public static final String SAME_CONDITION_NODE = "互斥网关，同一个节点不能有相同跳转条件，跳转同一个目标节点!";

    /**
     * 并行网关存在多个出口指向同一目标节点。
     */
    public static final String SAME_DEST_NODE = "并行网关，同一个节点不能跳转同一个目标节点!";

    /**
     * 流程定义中开始节点数量超过一个。
     */
    public static final String MUL_START_NODE = "开始节点不能超过1个!";

    /**
     * 普通节点同时通过或退回到多个中间节点，缺少网关承接。
     */
    public static final String MUL_SKIP_BETWEEN = "不可同时通过或者退回到多个中间节点，必须先流转到网关节点!";

    /**
     * 开始节点出口数量超过一个。
     */
    public static final String MUL_START_SKIP = "节点流转条件不能超过1个!";

    /**
     * 流程定义未配置开始节点。
     */
    public static final String LOST_START_NODE = "流程缺少开始节点!";

    /**
     * 节点编码为空。
     */
    public static final String LOST_NODE_CODE = "节点编码缺失";

    /**
     * 开始节点或中间节点未配置跳转线。
     */
    public static final String MUST_SKIP = "开始或者中间节点必须画跳转线";

    /**
     * 同一流程定义下节点编码重复。
     */
    public static final String SAME_NODE_CODE = "同一流程中节点编码重复!";

    /**
     * 跳转操作未配置目标节点。
     */
    public static final String NULL_DEST_NODE = "无法他跳转，未配置目标节点!";

    /**
     * 未找到指定跳转类型对应的目标节点。
     */
    public static final String NULL_SKIP_TYPE = "未找到跳转类型匹配的目标节点!";

    /**
     * 条件或网关路由没有命中任何可跳转出口。
     */
    public static final String NULL_CONDITION_VALUE_NODE = "未找到跳转条件，不支持跳转!";

    /**
     * 连续网关路由形成循环，无法解析出实际办理节点。
     */
    public static final String GATEWAY_CYCLE = "网关路由存在循环，无法解析目标节点!";

    /**
     * 跳转条件表达式为空。
     */
    public static final String NULL_CONDITION_VALUE = "跳转条件不能为空!";

    /**
     * 未注册条件表达式策略。
     */
    public static final String NULL_CONDITION_STRATEGY = "条件表达式策略不能为空!";

    /**
     * 未注册办理人表达式策略。
     */
    public static final String NULL_VARIABLE_STRATEGY = "办理人表达式策略不能为空!";

    /**
     * 未注册监听器表达式策略。
     */
    public static final String NULL_LISTENER_STRATEGY = "办理人表达式策略不能为空!";

    /**
     * 未注册票签表达式策略。
     */
    public static final String NULL_VOTESIGN_STRATEGY = "票签表达式策略不能为空!";

    /**
     * 不允许退回到开始节点后的第一个节点之前。
     */
    public static final String FIRST_FORBID_BACK = "禁止退回到第一个节点";

    /**
     * 当前办理人无权跳转到目标节点。
     */
    public static final String NULL_ROLE_NODE = "无法跳转到该节点,请检查当前用户是否有权限!";

    /**
     * 目标节点对象为空。
     */
    public static final String LOST_DEST_NODE = "目标节点为空!";

    /**
     * 当前实例所在节点丢失。
     */
    public static final String LOST_CUR_NODE = "当前流程节点丢失!";

    /**
     * 指定目标节点编码不存在。
     */
    public static final String NULL_NODE_CODE = "目标节点编码不存在!";

    /**
     * 启动流程时业务 ID 为空。
     */
    public static final String NULL_BUSINESS_ID = "业务id为空!";

    /**
     * 流程编码为空。
     */
    public static final String NULL_FLOW_CODE = "流程编码缺失!";

    /**
     * 未找到流程定义。
     */
    public static final String NOT_FOUNT_DEF = "流程定义不存在!";

    /**
     * 已发布或已失效的流程定义不允许修改。
     */
    public static final String PUBLISHED_DEF_NOT_EDITABLE = "已发布或已失效的流程定义不可修改，请复制新版本后编辑!";

    /**
     * 未找到流程实例。
     */
    public static final String NOT_FOUNT_INSTANCE = "流程实例获取失败!";

    /**
     * 流程实例 ID 为空。
     */
    public static final String NULL_INSTANCE_ID = "流程实例id不能为空!";

    /**
     * 待办任务 ID 为空。
     */
    public static final String NULL_TASK_ID = "任务id不能为空!";

    /**
     * 未找到待办任务。
     */
    public static final String NOT_FOUNT_TASK = "未找到待办任务!";

    /**
     * 抄送人不能为空。
     */
    public static final String NULL_COPY_HANDLER = "抄送人不能为空!";

    /**
     * 抄送人无法解析为具体用户。
     */
    public static final String UNRESOLVED_COPY_HANDLER = "抄送人无法解析为具体用户!";

    /**
     * 当前接口不支持一次处理多个待办任务。
     */
    public static final String TASK_NOT_ONE = "此接口不能同时跳转多个待办任务，请更换!";

    /**
     * 流程定义 ID 为空。
     */
    public static final String NOT_DEFINITION_ID = "流程定义id不能为空!";

    /**
     * 流程节点数据为空或不完整。
     */
    public static final String NOT_NODE_DATA = "流程节点数据缺失!";

    /**
     * 流程定义已有运行实例，不允许执行指定操作。
     */
    public static final String EXIST_START_TASK = "流程定义已开启过审批任务，不可操作!";

    /**
     * 流程已处于完成或终态。
     */
    public static final String FLOW_FINISH = "流程已完成！";

    /**
     * 当前用户权限不足。
     */
    public static final String NOT_AUTHORITY = "请检查当前用户是否有权限!";

    /**
     * 会签或票签操作缺少办理人标识。
     */
    public static final String SIGN_NULL_HANDLER = "会签票签时，办理人标识不能为空";

    /**
     * 减签时办理人不足。
     */
    public static final String REDUCTION_SIGN_ONE_ERROR = "办理人不足或者只有一人，不可减签";

    /**
     * 加签对象已经是当前待办办理人。
     */
    public static final String IS_ALREADY_SIGN = "已经是待办人，不可加签";

    /**
     * 转办对象已经是当前转办人。
     */
    public static final String IS_ALREADY_TRANSFER = "已经是转办人，不可转办";

    /**
     * 委托对象已经是当前受托人。
     */
    public static final String IS_ALREADY_DEPUTE = "已经是受托人，不可委托";

    /**
     * 流程定义或实例未激活。
     */
    public static final String NOT_ACTIVITY = "当前流程定义或者实例已经挂起，请先激活";

    /**
     * 流程定义挂起时不允许启动新实例。
     */
    public static final String NOT_DEFINITION_ACTIVITY = "当前流程定义已挂起，不可开启新的流程";

    /**
     * 流程定义已经处于激活状态。
     */
    public static final String DEFINITION_ALREADY_ACTIVITY = "当前流程定义已经激活";

    /**
     * 流程定义已经处于挂起状态。
     */
    public static final String DEFINITION_ALREADY_SUSPENDED = "当前流程定义已经挂起";

    /**
     * 流程实例已经处于激活状态。
     */
    public static final String INSTANCE_ALREADY_ACTIVITY = "当前流程实例已经激活";

    /**
     * 流程实例已经处于挂起状态。
     */
    public static final String INSTANCE_ALREADY_SUSPENDED = "当前流程实例已经挂起";

    /**
     * 表单已经处于发布状态。
     */
    public static final String FORM_ALREADY_PUBLISH = "当前表单状态已发布";

    /**
     * 表单已经处于未发布状态。
     */
    public static final String FORM_ALREADY_UN_PUBLISH = "当前表单状态未发布";

    /**
     * 按表单编码和版本查询到的数据不唯一。
     */
    public static final String FORM_NOT_ONE = "表单数据错误, 请联系管理员排查!";

    /**
     * 主键 ID 为空。
     */
    public static final String ID_EMPTY = "ID不能为空";

    /**
     * 非流程发起人不能撤销流程。
     */
    public static final String NOT_DEF_PROMOTER_NOT_CANCEL = "不是当前流程的发起人，无法撤销";

    /**
     * 办理人为空。
     */
    public static final String HANDLER_NOT_EMPTY = "办理人不能为空";

    /**
     * 指定目标节点为网关节点，当前操作不允许直接跳到网关。
     */
    public static final String TAR_NOT_GATEWAY = "目标节点不能是网关节点!";

    /**
     * 未获取到流程任务。
     */
    public static final String NOT_FOUND_FLOW_TASK = "未获取到流程任务";

    /**
     * 流程图存在不可达或无效跳转线。
     */
    public static final String FLOW_HAVE_USELESS_SKIP = "存在无用的跳转";

    /**
     * 转办目标办理人为空。
     */
    public static final String NULL_TRANSFER_HANDLER = "转办对象不能为空";

    /**
     * 委托目标办理人为空。
     */
    public static final String NULL_DEPUTE_HANDLER = "委托对象不能为空";

    /**
     * 加签目标办理人为空。
     */
    public static final String NULL_ADD_SIGNATURE_HANDLER = "加签对象不能为空";

    /**
     * 减签目标办理人为空。
     */
    public static final String NULL_REDUCTION_SIGNATURE_HANDLER = "减签对象不能为空";

    /**
     * 输入流读取失败。
     */
    public static final String READ_IS_ERROR = "读取is流失败";

    /**
     * 表单已被流程引用，不允许执行指定操作。
     */
    public static final String EXIST_USE_FORM = "流程表单已使用，不可操作!";

    /**
     * 未找到前置任务。
     */
    public static final String NOT_FOUNT_LAST_TASK = "未找到前置任务!";

    /**
     * 未找到当前办理人已办理过的任务。
     */
    public static final String NOT_FOUNT_HANDLED_TASK = "未找到您已办理过的任务!";

    /**
     * 拿回任务时目标任务未办理。
     */
    public static final String NOT_FOUNT_HANDLED_TASK_HANDLER = "拿回的任务未办理!";

    /**
     * 开始节点不允许作为跳转目标。
     */
    public static final String START_NODE_NOT_ALLOW_JUMP = "开始节点不允许跳转!";

    /**
     * 未绘制流程图时不允许发布。
     */
    public static final String NOT_DRAW_FLOW_ERROR = "未绘制流程图，不可发布";

    /**
     * 服务任务节点配置为空或不是合法 JSON 对象。
     */
    public static final String NULL_SERVICE_TASK_CONFIG = "服务任务节点配置不能为空";

    /**
     * 服务任务节点配置缺少 serviceTask 段。
     */
    public static final String NULL_SERVICE_TASK_SECTION = "服务任务节点缺少 serviceTask 配置段";

    /**
     * 服务任务 type 不是 class / delegateExpression / expression。
     */
    public static final String ILLEGAL_SERVICE_TASK_TYPE = "服务任务 type 只能是 class / delegateExpression / expression";

    /**
     * 服务任务 class 模式缺少 handler，或表达式模式缺少 expression。
     */
    public static final String NULL_SERVICE_TASK_EXPRESSION = "服务任务处理器或表达式不能为空";

    /**
     * resultVariable 只允许出现在 expression 模式。
     */
    public static final String ILLEGAL_RESULT_VARIABLE = "服务任务 resultVariable 仅支持 expression 模式";

    /**
     * 服务任务节点出口连线只允许 PASS。
     */
    public static final String SERVICE_TASK_ONLY_PASS = "服务任务节点只允许 PASS 出口";

    /**
     * 服务任务表达式未匹配到已注册策略。
     */
    public static final String NULL_SERVICE_TASK_STRATEGY = "服务任务表达式未匹配到已注册策略";

    /**
     * 一次流程操作内自动节点执行次数超过安全上限。
     */
    public static final String SERVICE_TASK_LOOP_LIMIT = "自动节点执行次数超过安全上限，可能存在服务任务环路";

    /**
     * 未找到处理服务任务节点的自动节点执行器。
     */
    public static final String NULL_SERVICE_TASK_EXECUTOR = "未找到服务任务自动节点执行器";

    /**
     * 事件 JSON 配置的 timerType 不是 AFTER / FIXED / POINT_IN_TIME。
     */
    public static final String ILLEGAL_EVENT_TIMER_TYPE = "事件定时配置 timerType 只能是 AFTER / FIXED / POINT_IN_TIME";

    /**
     * 事件定时配置缺少 timerValue。
     */
    public static final String NULL_EVENT_TIMER_VALUE = "事件定时配置缺少 timerValue";

    /**
     * 事件定时配置的 timerValue 不是合法的 ISO-8601 时长（AFTER / FIXED）。
     */
    public static final String ILLEGAL_EVENT_TIMER_DURATION = "定时时间值格式非法，应为 ISO-8601 时长（如 PT30M、P3D）";

    /**
     * 事件定时配置的 timerValue 不是合法的每日时间点集合（POINT_IN_TIME，如 ["09:00","18:00"]）。
     */
    public static final String ILLEGAL_EVENT_TIME_POINT = "指定时间点格式非法，应为每日时间点集合（如 [\"09:00\",\"18:00\"]）";

    /**
     * 事件定时配置的 maxCount 不是正整数。
     */
    public static final String ILLEGAL_EVENT_MAX_COUNT = "事件定时配置 maxCount 只能是正整数";

    /**
     * 事件定时配置的 timezone 不是合法的 IANA 时区标识（如 Asia/Shanghai）。
     */
    public static final String ILLEGAL_EVENT_TIMEZONE = "事件定时配置 timezone 非法";

    /**
     * 未知或非法的事件类型，无法映射到 {@code EventType}。
     */
    public static final String ILLEGAL_EVENT_TYPE = "未知的事件类型";

    /**
     * 事件节点必须使用中间节点（BETWEEN）承载，固定开始 / 结束、网关与服务任务节点不允许事件属性。
     */
    public static final String EVENT_NODE_TYPE_INVALID = "事件节点必须使用中间节点类型";

    /**
     * 固定开始 / 结束节点不允许配置事件属性。
     */
    public static final String FIXED_NODE_NO_EVENT = "固定开始 / 结束节点不允许配置事件属性";

    /**
     * 固定开始节点不能有入线，固定结束节点不能有出线。
     */
    public static final String FIXED_NODE_LINE_ERROR = "固定开始节点不能有入线，固定结束节点不能有出线";

    /**
     * 启动事件必须紧邻固定开始节点：只有来自开始节点的一条入线和一条普通出口。
     */
    public static final String START_EVENT_TOPOLOGY = "启动事件必须紧邻固定开始节点，且只有一条来自开始节点的入线和一条普通出口";

    /**
     * 结束事件必须有入线，且唯一出口直接指向固定结束节点。
     */
    public static final String END_EVENT_TOPOLOGY = "结束事件必须有入线，且唯一出口直接指向固定结束节点";

    /**
     * 捕获事件必须有出线。
     */
    public static final String CATCH_EVENT_NO_OUTGOING = "捕获事件必须有出线";

    /**
     * 抛出事件必须有入线。
     */
    public static final String THROW_EVENT_NO_INCOMING = "抛出事件必须有入线";

    /**
     * 边界事件必须配置附着节点编码。
     */
    public static final String BOUNDARY_EVENT_NO_ATTACHED = "边界事件必须配置附着节点编码";

    /**
     * 边界事件附着的任务节点在当前定义中不存在。
     */
    public static final String BOUNDARY_EVENT_ATTACHED_NOT_EXIST = "边界事件附着的任务节点不存在";

    /**
     * 边界事件只能附着到未配置事件的中间任务节点。
     */
    public static final String BOUNDARY_EVENT_ATTACHED_INVALID = "边界事件只能附着到未配置事件的中间任务节点";

    /**
     * 第一阶段边界事件必须为中断型。
     */
    public static final String BOUNDARY_EVENT_NOT_INTERRUPTING = "第一阶段边界事件必须为中断型";

    /**
     * 消息事件必须配置消息名称。
     */
    public static final String MESSAGE_EVENT_NO_NAME = "消息事件必须配置消息名称";

    /**
     * 定时事件必须配置合法的 node_config.event 段。
     */
    public static final String TIMER_EVENT_NO_CONFIG = "定时事件必须配置合法的定时参数";

    /**
     * 同一定义不能同时存在消息启动与定时启动事件。
     */
    public static final String EVENT_MIX_START_TYPE = "同一定义不能混用消息启动与定时启动事件";

    /**
     * 并行 / 包容网关不允许同时激活多个无待办的事件等待分支。
     */
    public static final String GATEWAY_MULTI_EVENT_WAIT_BRANCHES
        = "并行/包容网关的扇出分支中事件等待分支最多一个";

    /**
     * 事件类型尚未开放运行时能力，只能保存为未发布草稿。
     */
    public static final String EVENT_NOT_PUBLISHABLE = "当前版本尚未开放该事件类型的运行时，只能保存为未发布草稿";

    /**
     * 启动事件拓扑与等待记录不匹配，等待记录将被取消。
     */
    public static final String EVENT_START_TOPOLOGY_MISMATCH = "启动事件拓扑与等待记录不匹配";

    /**
     * 手动启动不能绕过定时 / 消息启动事件。
     */
    public static final String MANUAL_START_EVENT_FORBIDDEN = "该流程由启动事件触发，不支持手动启动";

    /**
     * 未找到消息名称匹配的消息事件节点。
     */
    public static final String NOT_FOUND_MESSAGE_EVENT = "未找到消息名称匹配的消息事件节点";

    /**
     * 未找到匹配的消息等待记录。
     */
    public static final String NOT_FOUND_MESSAGE_WAIT = "未找到匹配的消息等待记录";

    /**
     * 消息等待记录已被抢占或不可触发。
     */
    public static final String MESSAGE_WAIT_ALREADY_CLAIMED = "消息等待记录已被抢占或不可触发";

    /**
     * 流程错误未被捕获：实例内没有匹配的错误捕获或错误边界等待。
     */
    public static final String EVENT_ERROR_UNCAUGHT = "流程错误未被捕获";

    /**
     * 错误等待记录已被处理或不可触发。
     */
    public static final String EVENT_ERROR_ALREADY_PROCESSED = "错误等待记录已被处理或不可触发";

    /**
     * 消息抛出 / 消息结束事件未注册发布器：发布无法完成，随调用方事务失败。
     */
    public static final String EVENT_PUBLISHER_MISSING = "消息事件未注册 EventPublisher，发布失败";

    /**
     * 未找到结束节点。
     */
    public static final String NOT_FOUND_END_NODE = "未找到流程结束节点";

    /**
     * 未找到事件等待记录。
     */
    public static final String NOT_FOUND_EVENT_WAIT = "事件等待记录不存在";

    /**
     * 只有已死（{@code DEAD}）的等待记录可以复活。
     */
    public static final String EVENT_WAIT_REVIVE_NOT_DEAD = "只有已死（DEAD）的等待记录可以复活";

    /**
     * 定时启动事件的等待记录不支持复活：重新发布或挂起后激活定义会按当前定义重建记录。
     */
    public static final String EVENT_WAIT_REVIVE_TIMER_START
        = "定时启动事件的等待记录不支持复活，请重新发布或挂起后激活流程定义";

    /**
     * 等待记录所属实例不存在或已不可用，复活后也无法推进。
     */
    public static final String EVENT_WAIT_REVIVE_INSTANCE_UNAVAILABLE = "流程实例不存在或已不可用，等待记录不能复活";

    /**
     * 实例已越过该事件节点，复活的等待记录会去推进一个已办结节点。
     */
    public static final String EVENT_WAIT_REVIVE_NODE_PASSED = "实例已越过该事件节点，等待记录不能复活";

    /**
     * 边界事件附着的待办已不存在，复活后也无法中断。
     */
    public static final String EVENT_WAIT_REVIVE_TASK_MISSING = "边界事件附着的待办已不存在，等待记录不能复活";

    /**
     * 复活时按主键与版本号条件更新未命中：记录已被并发修改，需重新读取。
     */
    public static final String EVENT_WAIT_REVIVE_CONFLICT = "等待记录状态已变化，请刷新后重试";

}
