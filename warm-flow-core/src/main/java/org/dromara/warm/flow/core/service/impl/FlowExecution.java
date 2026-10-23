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
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.spi.ListenerVariable;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程执行作用域：一次流程操作期间已加载聚合（任务、实例、定义、当前节点、定义图）的统一存放处。
 *
 * <p>包内私有，零公共面。加载方法的校验顺序逐条镜像既有实现的次序（原任务级加载器与撤回入口），
 * 异常消息与触发顺序是行为契约，不得增删或重排。</p>
 *
 * <p>定义图（combine）与办理人全集同策略：一次操作内只加载一次，操作内所有消费点
 * 复用同一引用，回调后不重载——监听器不可达 combine 内部列表，办理人视图则与权限校验
 * 共用同一份快照。变量相反：合并只发生在既有调用点，并保持向 {@code intent}
 * 原地写回合并结果的现状语义。</p>
 *
 * @author warm
 */
final class FlowExecution {

    /**
     * 当前待办任务，仅任务级操作存在。
     */
    final Task task;

    /**
     * 流程实例。任务级/实例级操作在构造期固定；发起（start）链在实例创建步骤回填一次。
     */
    Instance instance;

    /**
     * 流程定义。
     */
    final Definition definition;

    /**
     * 当前节点，任务级操作与发起链加载；实例级操作不强行伪造。
     */
    final Node nowNode;

    /**
     * 调用方意图，引擎编排原则上不写（既有兼容写回见加载与合并方法）；读取路径（load）为 null。
     */
    final WorkflowContext intent;

    /**
     * 定义图缓存，经 {@link #loadCombine()} / {@link #loadCombineNoDef()} 懒加载。
     */
    private FlowCombine combine;

    /**
     * 办理人全集缓存，经 {@link #loadTaskUsers()} 懒加载。
     */
    private List<User> taskUsers;

    /**
     * 加载任务执行需要的实例、定义和当前节点，并统一校验可执行状态。
     *
     * @param task   当前待办任务（复用调用方已加载的对象，不重复查询）
     * @param intent 调用方上下文
     * @param op     发起操作（guard 表行键，决定状态前置谓词集合）
     * @return 执行作用域
     */
    static FlowExecution loadTask(Task task, WorkflowContext intent, FlowOp op) {
        AssertUtil.isNull(task, ExceptionCons.NOT_FOUNT_TASK);
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowStatusMachine.checkGuards(op, definition, instance);
        Node nowNode = FlowEngine.nodeService().getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
        AssertUtil.isNull(nowNode, ExceptionCons.LOST_CUR_NODE);
        return new FlowExecution(task, instance, definition, nowNode, intent);
    }

    /**
     * 加载实例级操作需要的实例与定义并校验，撤回（revoke）的默认状态补写与变量合并
     * 保留在原有次序：仅空时补 CANCEL → 实例判空 → 变量合并 → 定义判空 → 激活 → 终态 → 结束节点。
     * 不自动加载待办列表，撤回的两次任务查询仍由原编排点分别发起。
     *
     * @param instanceId 流程实例ID
     * @param intent     调用方上下文
     * @return 执行作用域
     */
    static FlowExecution loadInstance(Long instanceId, WorkflowContext intent) {
        if (StringUtils.isEmpty(intent.getFlowStatus())) {
            intent.setFlowStatus(FlowStatusMachine.mapper().instanceStatus(FlowStatus.CANCEL));
        }
        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        intent.setVariables(MapUtil.mergeAll(instance.getVariableMap(), intent.getVariables()));
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowStatusMachine.checkGuards(FlowOp.REVOKE, definition, instance);
        return new FlowExecution(null, instance, definition, null, intent);
    }

    /**
     * 加载发起流程需要的已发布定义、定义图与开始节点并校验。发起的定义来自发布编码检索，
     * 此时实例尚不存在，挂起守卫只看定义（消息为「不可开启新的流程」，与 guard 表的
     * NOT_ACTIVITY 不同，不共用行）。校验顺序逐条镜像原 {@code InsServiceImpl.start}：
     * 定义判空 → 定义图 → 开始节点 → 定义激活。task/instance 为空，实例由发起链创建后回填。
     *
     * @param flowCode 流程编码
     * @param intent   调用方上下文
     * @return 执行作用域
     */
    static FlowExecution loadStart(String flowCode, WorkflowContext intent) {
        // 获取已发布的流程节点
        Definition definition = FlowEngine.defService().getPublishByFlowCode(flowCode);
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowCombine combine = FlowEngine.defService().getFlowCombine(definition);
        // 获取开始节点
        Node startNode = StreamUtils.filterOne(combine.getAllNodes(), node -> NodeType.isStart(node.getNodeType()));
        AssertUtil.isNull(startNode, ExceptionCons.LOST_START_NODE);
        // 判断流程定义是否激活状态
        AssertUtil.isTrue(definition.getActivityStatus().equals(ActivityStatus.SUSPENDED.getKey())
            , ExceptionCons.NOT_DEFINITION_ACTIVITY);
        // 手动启动不能绕过启动事件：固定 START 的直接后继是启动事件节点时拒绝
        List<Node> startSuccessors = FlowEngine.nodeService().getNextNodeList(startNode, null
            , SkipType.PASS.getKey(), null, new PathWayData(), combine);
        AssertUtil.isTrue(CollUtil.isNotEmpty(startSuccessors)
            && EventType.isStart(startSuccessors.get(0).getEventType())
            , ExceptionCons.MANUAL_START_EVENT_FORBIDDEN);
        FlowExecution execution = new FlowExecution(null, null, definition, startNode, intent);
        // 已查询的定义图注入缓存：发起链经 loadCombine() 复用同一引用，不再重查
        execution.combine = combine;
        return execution;
    }

    /**
     * 加载定时启动事件触发所需的已发布定义、定义图与启动事件节点并校验：
     * 定义必须处于发布且激活状态；固定 START 的直接后继必须是指定编码的
     * {@code TIMER_START} 事件节点。
     *
     * @param definitionId 流程定义主键
     * @param nodeCode     启动事件节点编码
     * @param intent       调用方上下文（无人值守时操作人由调用方按系统兜底传入）
     * @return 执行作用域，当前节点为启动事件节点
     */
    static FlowExecution loadTimerStart(Long definitionId, String nodeCode, WorkflowContext intent) {
        Definition definition = FlowEngine.defService().getById(definitionId);
        if (definition == null) {
            throw new TimerStartTopologyException(ExceptionCons.NOT_FOUNT_DEF);
        }
        FlowCombine combine = FlowEngine.defService().getFlowCombine(definition);
        Node startNode = StreamUtils.filterOne(combine.getAllNodes(), node -> NodeType.isStart(node.getNodeType()));
        if (startNode == null) {
            throw new TimerStartTopologyException(ExceptionCons.LOST_START_NODE);
        }
        List<Node> successors = FlowEngine.nodeService().getNextNodeList(startNode, null
            , SkipType.PASS.getKey(), null, new PathWayData(), combine);
        if (CollUtil.isEmpty(successors)) {
            throw new TimerStartTopologyException(ExceptionCons.LOST_CUR_NODE);
        }
        Node eventNode = successors.get(0);
        if (!EventType.TIMER_START.getValue().equals(eventNode.getEventType())
            || nodeCode == null || !nodeCode.equals(eventNode.getNodeCode())) {
            throw new TimerStartTopologyException(ExceptionCons.EVENT_START_TOPOLOGY_MISMATCH);
        }
        if (definition.getActivityStatus().equals(ActivityStatus.SUSPENDED.getKey())) {
            throw new TimerStartTopologyException(ExceptionCons.NOT_DEFINITION_ACTIVITY);
        }
        FlowExecution execution = new FlowExecution(null, null, definition, eventNode, intent);
        execution.combine = combine;
        return execution;
    }

    /** 仅标记已确认的定时启动拓扑失效；查询和解析异常交由调度器重试。 */
    static final class TimerStartTopologyException extends RuntimeException {
        TimerStartTopologyException(String message) {
            super(message);
        }
    }

    /**
     * 加载消息启动事件触发所需的已发布定义、定义图与启动事件节点并校验：
     * 固定 START 的直接后继必须是消息名称匹配的 {@code MESSAGE_START} 事件节点。
     *
     * @param flowCode  流程编码
     * @param eventName 消息名称
     * @param intent    调用方上下文
     * @return 执行作用域，当前节点为启动事件节点
     */
    static FlowExecution loadMessageStart(String flowCode, String eventName, WorkflowContext intent) {
        Definition definition = FlowEngine.defService().getPublishByFlowCode(flowCode);
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowCombine combine = FlowEngine.defService().getFlowCombine(definition);
        Node startNode = StreamUtils.filterOne(combine.getAllNodes(), node -> NodeType.isStart(node.getNodeType()));
        AssertUtil.isNull(startNode, ExceptionCons.LOST_START_NODE);
        Node eventNode = eventStartSuccessor(startNode, combine, null, EventType.MESSAGE_START.getValue());
        AssertUtil.isTrue(!eventName.equals(eventNode.getEventName()), ExceptionCons.NOT_FOUND_MESSAGE_EVENT);
        AssertUtil.isTrue(definition.getActivityStatus().equals(ActivityStatus.SUSPENDED.getKey())
            , ExceptionCons.NOT_DEFINITION_ACTIVITY);
        FlowExecution execution = new FlowExecution(null, null, definition, eventNode, intent);
        execution.combine = combine;
        return execution;
    }

    /**
     * 校验固定 START 的直接后继是预期的启动事件节点（手动启动拒绝启动事件后继，
     * 定时 / 消息启动校验后继匹配）。
     *
     * @param startNode    固定开始节点
     * @param combine      定义图
     * @param nodeCode     期望的事件节点编码（消息启动按名称匹配时为 {@code null}）
     * @param expectedType 期望的事件类型（TIMER_START / MESSAGE_START）
     * @return 匹配的启动事件节点
     */
    private static Node eventStartSuccessor(Node startNode, FlowCombine combine, String nodeCode
        , String expectedType) {
        List<Node> successors = FlowEngine.nodeService().getNextNodeList(startNode, null
            , SkipType.PASS.getKey(), null, new PathWayData(), combine);
        AssertUtil.isEmpty(successors, ExceptionCons.LOST_CUR_NODE);
        Node eventNode = successors.get(0);
        boolean matched = expectedType.equals(eventNode.getEventType())
            && (nodeCode == null || nodeCode.equals(eventNode.getNodeCode()));
        AssertUtil.isTrue(!matched, ExceptionCons.EVENT_START_TOPOLOGY_MISMATCH);
        return eventNode;
    }

    /**
     * 加载事件触发（定时到期 / 消息到达）执行作用域：实例、定义、定义图与事件节点。
     *
     * @param instance 流程实例
     * @param nodeCode 事件节点编码
     * @param intent   触发上下文
     * @return 执行作用域，当前节点为事件节点
     */
    static FlowExecution loadForEvent(Instance instance, String nodeCode, WorkflowContext intent) {
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        FlowCombine combine = FlowEngine.defService().getFlowCombineNoDef(definition.getId());
        Node eventNode = StreamUtils.filterOne(combine.getAllNodes()
            , node -> node.getNodeCode().equals(nodeCode));
        AssertUtil.isNull(eventNode, ExceptionCons.LOST_CUR_NODE);
        FlowExecution execution = new FlowExecution(null, instance, definition, eventNode, intent);
        execution.combine = combine;
        return execution;
    }

    /**
     * 创建一次操作独占的执行作用域；统一由静态加载方法完成前置校验后调用。
     *
     * @param task       当前待办任务，实例级或启动操作可为空
     * @param instance   流程实例，启动操作创建前可为空
     * @param definition 流程定义
     * @param nowNode    当前节点，实例级操作可为空
     * @param intent     调用方上下文
     */
    private FlowExecution(Task task, Instance instance, Definition definition, Node nowNode
        , WorkflowContext intent) {
        this.task = task;
        this.instance = instance;
        this.definition = definition;
        this.nowNode = nowNode;
        this.intent = intent;
    }

    /**
     * 加载含定义对象的定义图（撤回路径使用），一次操作内只加载一次。
     *
     * @return 流程数据集合
     */
    FlowCombine loadCombine() {
        if (combine == null) {
            combine = FlowEngine.defService().getFlowCombine(definition);
        }
        return combine;
    }

    /**
     * 加载不含定义对象的定义图（办理路径使用，与既有 getFlowCombineNoDef 调用点对应），
     * 一次操作内只加载一次。
     *
     * @return 流程数据集合
     */
    FlowCombine loadCombineNoDef() {
        if (combine == null) {
            combine = FlowEngine.defService().getFlowCombineNoDef(definition.getId());
        }
        return combine;
    }

    /**
     * 加载当前任务的办理人全集，一次操作内只查询一次；办理路径与 {@code task.userList}
     * 共享同一引用。回调后不重载：监听器期间的办理人表写库不进入本次操作视图（R5 语义，
     * 与权限校验使用同一份快照）。
     *
     * @return 办理人全集
     */
    List<User> loadTaskUsers() {
        if (taskUsers == null) {
            taskUsers = FlowEngine.userService().listByAssociatedAndTypes(task.getId());
        }
        return taskUsers;
    }

    /**
     * 从办理人全集派生指定类型的视图（R5：内存过滤替代按类型 SQL 查询）。
     *
     * @param types 办理人类型
     * @return 命中类型的办理人视图
     */
    List<User> usersOfTypes(String... types) {
        return StreamUtils.filter(loadTaskUsers(), user -> Arrays.asList(types).contains(user.getType()));
    }

    /**
     * 在既有合并点合并实例变量与意图变量，并保持向意图原地写回合并结果的现状语义。
     *
     * @return 合并后的变量
     */
    Map<String, Object> mergeVariables() {
        Map<String, Object> merged = MapUtil.mergeAll(instance.getVariableMap(), intent.getVariables());
        intent.setVariables(merged);
        return merged;
    }

    /**
     * 补充节点的默认流程变量到意图变量：变量尚未创建时按需惰性创建，避免默认值得不到落库。
     *
     * @param node 当前 / 刚到达的节点
     */
    void applyNodeDefaults(Node node) {
        if (intent == null || node == null) {
            return;
        }
        Map<String, Object> defaults = NodeDefaults.of(node);
        if (defaults.isEmpty()) {
            return;
        }
        Map<String, Object> variables = intent.getVariables();
        if (variables == null) {
            variables = new LinkedHashMap<>();
            intent.setVariables(variables);
        }
        NodeDefaults.apply(variables, defaults);
    }

    /**
     * 构建不带调用方上下文的监听器变量（读取路径与不带 context 的既有监听点使用）。
     * 每次调用必须新建实例：endCreateListener 以游标方式原地修改传入对象，禁止缓存复用。
     *
     * @param task 监听器对应任务
     * @param node 监听器对应节点
     * @return 监听器变量
     */
    ListenerVariable rawListener(Task task, Node node) {
        return new ListenerVariable(definition, instance, node, intent.getVariables(), task);
    }

    /**
     * 构建附带调用方上下文的监听器变量。
     *
     * @param task 监听器对应任务
     * @param node 监听器对应节点
     * @return 监听器变量
     */
    ListenerVariable contextListener(Task task, Node node) {
        return rawListener(task, node).setContext(intent);
    }

    /**
     * 构建附带后续节点与新建任务集合的监听器变量，列表保持原引用不复制，
     * 原地修改时机（endCreateListener 逐节点推进）不变。
     *
     * @param task      监听器对应任务
     * @param node      监听器对应节点
     * @param nextNodes 后续节点集合
     * @param nextTasks 新建任务集合
     * @return 监听器变量
     */
    ListenerVariable contextListener(Task task, Node node, List<Node> nextNodes, List<Task> nextTasks) {
        return new ListenerVariable(definition, instance, node, intent.getVariables(), task
            , nextNodes, nextTasks).setContext(intent);
    }
}
