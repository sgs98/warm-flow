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
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 发起操作链：装配 6 个有序步骤并委托 {@link FlowPipeline} 执行，入口已通过
 * {@code FlowExecution.loadStart} 完成已发布定义、定义图与开始节点的加载校验。
 *
 * <p>与其他链的差异：实例不是加载期聚合而是链内创建——创建步骤将实例回填
 * {@code execution.instance}（此前 start 监听器看到 null 实例，此后 assignment/endCreate
 * 看到已创建实例，时机与原实现一致）；最后一步以 {@code Optional.of(instance)} 作为
 * 完成结果，链尾兜底不适用。</p>
 *
 * <p><b>生命周期锁</b>：每次发起操作新建实例，字段为本次操作中间态（路由结果、实例、
 * 历史任务、首待办）；非线程安全，禁止静态化或缓存复用。</p>
 *
 * <p>步骤体自原 {@code InsServiceImpl.start} 逐行搬移（原手工逐字段拷贝的 taskContext
 * 已删：addTask 只读取 instanceStatus，逐字段拷贝与直传调用方上下文等值），语句顺序
 * 与引用语义为行为契约，由特征测试锁定。</p>
 *
 * @author warm
 */
final class FlowStartChain {

    /**
     * 实例服务，复用既有实例持久化能力。
     */
    private final InsServiceImpl insService;
    /**
     * 业务id。
     */
    private final String businessId;

    /**
     * 路由结果。
     */
    private PathWayData pathWayData;
    /**
     * 后续节点集合。
     */
    private List<Node> nextNodes;
    /**
     * 流程实例（创建后回填执行作用域）。
     */
    private Instance instance;
    /**
     * 发起历史任务。
     */
    private HisTask hisTask;
    /**
     * 首待办任务。
     */
    private List<Task> addTasks;

    /**
     * @param insService  实例服务
     * @param businessId  业务id
     */
    FlowStartChain(InsServiceImpl insService, String businessId) {
        this.insService = insService;
        this.businessId = businessId;
    }

    /**
     * 按序装配发起链：开始监听器 → 路由 → 实例与历史 → 建首待办 → 元数据与分派 → 持久化与完成。
     *
     * @return 流水线
     */
    FlowPipeline pipeline() {
        return FlowPipeline.of(
            this::startListener,
            this::route,
            this::createInstanceAndHis,
            this::buildTasks,
            this::metadataAndAssignment,
            this::persistAndFinish);
    }

    private Optional<Instance> startListener(FlowExecution execution) {
        // 执行开始监听器（实例尚不存在，监听器变量中实例为 null）
        ListenerUtil.executeStart(execution.contextListener(null, execution.nowNode));
        return Optional.empty();
    }

    private Optional<Instance> route(FlowExecution execution) {
        // 获取下一个节点，如果是网关节点，则重新获取后续节点
        pathWayData = new PathWayData().setDefId(execution.nowNode.getDefinitionId())
            .setSkipType(SkipType.PASS.getKey());
        nextNodes = FlowEngine.nodeService().getNextNodeList(execution.nowNode, null, SkipType.PASS.getKey()
            , execution.intent.getVariables(), pathWayData, execution.loadCombine());
        return Optional.empty();
    }

    private Optional<Instance> createInstanceAndHis(FlowExecution execution) {
        // 设置流程实例对象（创建后回填作用域：此后 assignment/endCreate 监听器可见）
        instance = setStartInstance(nextNodes.get(0), execution.intent);
        execution.instance = instance;

        // 设置历史任务
        hisTask = setHisTask(nextNodes, execution.intent, execution.nowNode, instance.getId());
        return Optional.empty();
    }

    private Optional<Instance> buildTasks(FlowExecution execution) {
        // 设置首待办任务（原手工逐字段拷贝的 taskContext 已删，直传调用方上下文等值）
        addTasks = StreamUtils.toList(nextNodes, node -> FlowEngine.taskService()
            .addTask(node, instance, execution.definition, execution.intent, SkipType.PASS.getKey()));

        // 办理人变量替换
        if (CollUtil.isNotEmpty(addTasks)) {
            ExpressionUtil.evalVariable(addTasks, execution.intent.getVariables()
                , execution.intent.getNextHandlers(), execution.intent.isNextHandlerAppend());
        }
        return Optional.empty();
    }

    private Optional<Instance> metadataAndAssignment(FlowExecution execution) {
        // 设置流程图元数据
        pathWayData.getTargetNodes().addAll(nextNodes);
        instance.setDefJson(FlowEngine.chartService().startMetadata(pathWayData));

        // 执行分派监听器
        ListenerUtil.executeAssignment(execution.contextListener(null, execution.nowNode, nextNodes, addTasks));
        return Optional.empty();
    }

    private Optional<Instance> persistAndFinish(FlowExecution execution) {
        // 开启流程，保存流程信息
        saveFlowInfo(instance, addTasks, hisTask, execution.intent);

        // 执行完成和创建监听器
        ListenerUtil.endCreateListener(execution.contextListener(null, execution.nowNode, nextNodes, addTasks));
        return Optional.of(instance);
    }

    /**
     * 设置流程实例对象
     *
     * @param firstBetweenNode 第一个中间节点
     * @param context          流程执行上下文
     * @return 流程实例
     */
    private Instance setStartInstance(Node firstBetweenNode, WorkflowContext context) {
        Instance instance = FlowEngine.newIns();
        Date now = new Date();
        FlowEngine.dataFillHandler().idFill(instance);
        // 关联业务id,其实后面可以不用到业务id,传业务id目前来看只是为了批量创建流程的时候能创建出有区别化的流程,也是为了后期需要用到businessId。
        instance.setDefinitionId(firstBetweenNode.getDefinitionId())
            .setBusinessId(businessId)
            .setNodeType(firstBetweenNode.getNodeType())
            .setNodeCode(firstBetweenNode.getNodeCode())
            .setNodeName(firstBetweenNode.getNodeName())
            .setFlowStatus(StringUtils.emptyDefault(context.getInstanceStatus()
                , FlowStatus.TOBESUBMIT.getKey()))
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey())
            .setVariable(FlowEngine.jsonConvert.objToStr(context.getVariables()))
            .setCreateTime(now)
            .setUpdateTime(now)
            .setCreateBy(context.getHandler())
            .setUpdateBy(context.getHandler())
            .setExt(context.getExt());
        return instance;
    }

    /**
     * 设置历史任务
     *
     * @param nextNodes  下一节点集合
     * @param context    流程执行上下文
     * @param startNode  开始节点
     * @param instanceId 流程实例id
     */
    private HisTask setHisTask(List<Node> nextNodes, WorkflowContext context, Node startNode, Long instanceId) {
        Task startTask = FlowEngine.newTask()
            .setInstanceId(instanceId)
            .setDefinitionId(startNode.getDefinitionId())
            .setNodeCode(startNode.getNodeCode())
            .setNodeName(startNode.getNodeName())
            .setNodeType(startNode.getNodeType());
        FlowEngine.dataFillHandler().idFill(startTask);
        // 开始任务转历史任务
        return FlowEngine.hisTaskService().setSkipInsHis(startTask, nextNodes, context, SkipType.PASS.getKey());
    }

    /**
     * 开启流程，保存流程信息
     *
     * @param instance 流程实例
     * @param addTasks 新增任务
     * @param hisTask  历史任务
     */
    private void saveFlowInfo(Instance instance, List<Task> addTasks, HisTask hisTask, WorkflowContext context) {
        // 启动状态由调用方决定，首任务状态不能反向覆盖流程实例状态。
        String startStatus = instance.getFlowStatus();
        FlowEngine.taskService().setInsFinishInfo(instance, addTasks, context.getVariables());
        instance.setFlowStatus(startStatus);
        FlowEngine.hisTaskService().save(hisTask);
        // 待办任务设置处理人
        if (CollUtil.isNotEmpty(addTasks)) {
            List<User> users = FlowEngine.userService().taskAddUsers(addTasks);
            FlowEngine.taskService().saveBatch(addTasks);
            FlowEngine.userService().saveBatch(users);
        }
        insService.save(instance);
    }
}
