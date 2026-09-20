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

}
