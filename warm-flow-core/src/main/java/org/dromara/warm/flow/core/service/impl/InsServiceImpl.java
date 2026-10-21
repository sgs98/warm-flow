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
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.orm.dao.FlowInstanceDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.timer.TimerWorkerId;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 流程实例服务实现。
 *
 * <p>负责启动流程、实例启停、级联清理实例运行数据，以及维护实例变量。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
public class InsServiceImpl extends WarmServiceImpl<FlowInstanceDao<Instance>, Instance> implements InsService {

    private static final Logger log = LoggerFactory.getLogger(InsServiceImpl.class);

    /**
     * 注入流程实例 DAO。
     *
     * @param warmDao 流程实例数据访问对象
     * @return 当前服务实例
     */
    @Override
    public InsService setDao(FlowInstanceDao<Instance> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 按业务标识和流程编码启动流程实例。
     *
     * @param businessId 业务主键
     * @param flowCode   流程编码
     * @param context    启动上下文
     * @return 已持久化的流程实例
     */
    @Override
    public Instance start(String businessId, String flowCode, WorkflowContext context) {
        AssertUtil.isNull(flowCode, ExceptionCons.NULL_FLOW_CODE);
        AssertUtil.isEmpty(businessId, ExceptionCons.NULL_BUSINESS_ID);
        FlowExecution execution = FlowExecution.loadStart(flowCode, context);
        return new FlowStartChain(this, businessId).pipeline().run(execution);
    }

    /**
     * 按流程编码 + 消息名称启动流程实例。
     *
     * @param businessId 业务主键
     * @param flowCode   流程编码
     * @param eventName  消息名称
     * @param payload    消息载荷
     * @param context    触发上下文
     * @return 已持久化的流程实例
     */
    @Override
    public Instance startByMessage(String businessId, String flowCode, String eventName
        , Map<String, Object> payload, WorkflowContext context) {
        AssertUtil.isNull(flowCode, ExceptionCons.NULL_FLOW_CODE);
        AssertUtil.isEmpty(businessId, ExceptionCons.NULL_BUSINESS_ID);
        AssertUtil.isEmpty(eventName, ExceptionCons.NOT_FOUND_MESSAGE_EVENT);
        if (MapUtil.isNotEmpty(payload)) {
            Map<String, Object> variables = context.getVariables();
            Map<String, Object> merged = variables == null ? new HashMap<>() : variables;
            merged.putAll(payload);
            context.setVariables(merged);
        }
        FlowExecution execution = FlowExecution.loadMessageStart(flowCode, eventName, context);
        return new FlowStartChain(this, businessId).pipeline().run(execution);
    }

    /**
     * 按定时启动事件手动发起一次实例
     * <p>
     * 复用 {@link FlowExecution#loadTimerStart}：与定时到期触发走同一套拓扑校验与发起链，
     * 唯一的差别是操作人由调用方给出、业务主键由调用方指定。等待记录完全不参与，
     * 定时任务的下次触发时间不受影响。
     *
     * @param businessId   业务主键
     * @param definitionId 流程定义主键
     * @param nodeCode     定时启动事件节点编码
     * @param payload      额外流程变量
     * @param context      触发上下文
     * @return 已持久化的流程实例
     */
    @Override
    public Instance startByTimer(String businessId, Long definitionId, String nodeCode
        , Map<String, Object> payload, WorkflowContext context) {
        AssertUtil.isNull(definitionId, ExceptionCons.NOT_DEFINITION_ID);
        AssertUtil.isEmpty(businessId, ExceptionCons.NULL_BUSINESS_ID);
        AssertUtil.isEmpty(nodeCode, ExceptionCons.LOST_CUR_NODE);
        if (MapUtil.isNotEmpty(payload)) {
            Map<String, Object> variables = context.getVariables();
            Map<String, Object> merged = variables == null ? new HashMap<>() : variables;
            merged.putAll(payload);
            context.setVariables(merged);
        }
        // 与定时到期触发保持一致：定时启动没有「发起人提交」环节，实例直接进入审批中，
        // 而不是落到开始节点的「待提交」——否则补发的件在待办列表里根本筛不出来
        context.setFlowStatus(FlowStatusMachine.mapper().instanceStatus(FlowStatus.WAITING));
        context.setTaskStatus(FlowStatusMachine.mapper().taskStatus(TaskStatus.PASS));
        FlowExecution execution = FlowExecution.loadTimerStart(definitionId, nodeCode, context);
        return new FlowStartChain(this, businessId).pipeline().run(execution);
    }

    /**
     * 按实例主键 + 消息名称触发等待中的消息事件。
     *
     * @param instanceId 流程实例主键
     * @param eventName  消息名称
     * @param payload    消息载荷
     * @param context    触发上下文
     * @return 触发后的流程实例
     */
    @Override
    public Instance triggerMessage(Long instanceId, String eventName
        , Map<String, Object> payload, WorkflowContext context) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        AssertUtil.isEmpty(eventName, ExceptionCons.NOT_FOUND_MESSAGE_EVENT);
        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);

        // 定位匹配的等待记录：实例内 MESSAGE_CATCH / MESSAGE_BOUNDARY 且消息名称相等
        EventWait target = null;
        for (EventWait wait : FlowEngine.eventWaitService().list(FlowEngine.newEventWait()
            .setInstanceId(instanceId))) {
            boolean typeMatched = EventType.MESSAGE_CATCH.getValue().equals(wait.getEventType())
                || EventType.MESSAGE_BOUNDARY.getValue().equals(wait.getEventType());
            boolean statusMatched = EventCons.STATUS_WAITING.equals(wait.getStatus())
                || EventCons.STATUS_RETRY.equals(wait.getStatus());
            if (typeMatched && statusMatched && eventName.equals(wait.getEventName())) {
                target = wait;
                break;
            }
        }
        AssertUtil.isNull(target, ExceptionCons.NOT_FOUND_MESSAGE_WAIT);

        // 消息载荷落库到等待记录：执行链从 wait.payload 读取并合并为流程变量，
        // 失败重试与租约恢复后仍可读取；同时写入内存态供本次同步处理直接读取。
        if (MapUtil.isNotEmpty(payload)) {
            String payloadJson = FlowEngine.jsonConvert.objToStr(payload);
            target.setPayload(payloadJson);
            FlowEngine.eventWaitService().bindPayload(target.getId(), target.getVersion(), payloadJson);
        }

        // 条件更新抢占：记录已是 RUNNING / CANCELLED / DEAD 时不得强行覆盖
        WarmFlow config = FlowEngine.getFlowConfig();
        long leaseMillis = config != null && config.getTimerLeaseSeconds() != null
            ? config.getTimerLeaseSeconds() * 1000L : 60_000L;
        Date now = new Date();
        boolean claimed = FlowEngine.eventWaitService().claimMessage(target.getId()
            , target.getVersion(), TimerWorkerId.get(), now, new Date(now.getTime() + leaseMillis));
        if (!claimed) {
            log.warn("消息等待记录已被抢占或不可触发，流程不变 instanceId={} eventName={}"
                , instanceId, eventName);
            return instance;
        }
        // 同步处理已抢占记录（完成 / 失败重试语义与定时路径一致），透传调用方上下文
        FlowEngine.eventWaitService().processClaimedMessage(target, context);
        return FlowEngine.insService().getById(instanceId);
    }

    /**
     * 实例级抛出流程错误，触发匹配的错误捕获事件。
     * <p>
     * 按 {@code instanceId + 错误码} 匹配 {@code ERROR_CATCH} 等待记录，精确错误码优先于通配；
     * 匹配成功后按条件更新抢占并沿错误捕获出口推进流程。实例内无任何匹配时抛出
     * 「流程错误未被捕获」异常，实例、任务与等待记录状态不变，由调用方事务回滚。
     *
     * @param instanceId   流程实例主键
     * @param errorCode    错误码
     * @param errorMessage 错误描述，仅用于日志与异常信息
     * @return 抛出成功返回 {@code true}
     */
    @Override
    public boolean throwError(Long instanceId, String errorCode, String errorMessage) {
        AssertUtil.isNull(instanceId, ExceptionCons.NULL_INSTANCE_ID);
        Instance instance = FlowEngine.insService().getById(instanceId);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);

        EventWait target = FlowEngine.eventWaitService().matchErrorWait(instanceId, null, errorCode);
        if (target == null) {
            log.warn("流程错误未被捕获，流程不变 instanceId={} errorCode={} message={}"
                , instanceId, errorCode, errorMessage);
            throw new FlowException("[" + (errorCode == null ? "" : errorCode) + "]"
                + ExceptionCons.EVENT_ERROR_UNCAUGHT
                + (StringUtils.isEmpty(errorMessage) ? "" : "：" + errorMessage));
        }
        return FlowEngine.eventWaitService().triggerErrorWait(target, errorCode, errorMessage);
    }

    /**
     * 按流程定义主键集合查询实例。
     *
     * @param defIds 流程定义主键集合
     * @return 命中的流程实例
     */
    @Override
    public List<Instance> listByDefIds(List<Long> defIds) {
        return getDao().getByDefIds(defIds);
    }

    /**
     * 删除流程实例及其等待事件、待办、历史任务和办理人数据。
     * <p>等待事件随实例一并删除，不留指向已删除实例的等待记录。
     *
     * @param instanceIds 流程实例主键集合
     * @return 是否删除成功
     */
    @Override
    public boolean remove(List<Long> instanceIds) {
        return toRemoveTask(instanceIds);
    }

    /**
     * 按流程定义主键查询实例。
     *
     * @param definitionId 流程定义主键
     * @return 该定义下的流程实例
     */
    @Override
    public List<Instance> getByDefId(Long definitionId) {
        return list(FlowEngine.newIns().setDefinitionId(definitionId));
    }

    /**
     * 按等待事件、办理人、待办、历史任务、实例的依赖顺序级联清理运行数据。
     *
     * @param instanceIds 流程实例主键集合
     * @return 是否删除成功
     */
    private boolean toRemoveTask(List<Long> instanceIds) {
        AssertUtil.isEmpty(instanceIds, ExceptionCons.NULL_INSTANCE_ID);

        // 级联删除实例关联的等待事件记录：
        // 与实例同批清理（逻辑 / 物理跟随引擎删除模式），避免实例删除后残留孤儿等待记录
        instanceIds.forEach(instanceId ->
            FlowEngine.eventWaitService().remove(FlowEngine.newEventWait().setInstanceId(instanceId)));

        List<Long> taskIds = new ArrayList<>();
        instanceIds.forEach(instanceId -> taskIds.addAll(
            FlowEngine.taskService()
                .list(FlowEngine.newTask().setInstanceId(instanceId))
                .stream()
                .map(Task::getId)
                .toList()));

        if (CollUtil.isNotEmpty(taskIds)) {
            FlowEngine.userService().deleteByTaskIds(taskIds);
        }

        // 抄送人挂在抄送历史主键上（见 CopyAdvancer），删除待办不会带走它们；
        // 这里按历史任务主键一并清理，避免实例删除后残留孤儿抄送人记录
        List<Long> hisTaskIds = new ArrayList<>();
        instanceIds.forEach(instanceId -> hisTaskIds.addAll(
            FlowEngine.hisTaskService()
                .list(FlowEngine.newHisTask().setInstanceId(instanceId))
                .stream()
                .map(HisTask::getId)
                .toList()));

        if (CollUtil.isNotEmpty(hisTaskIds)) {
            FlowEngine.userService().deleteByTaskIds(hisTaskIds);
        }

        FlowEngine.taskService().deleteByInsIds(instanceIds);
        FlowEngine.hisTaskService().deleteByInsIds(instanceIds);
        return FlowEngine.insService().removeByIds(instanceIds);
    }

    /**
     * 激活已挂起的流程实例。
     *
     * @param id 流程实例主键
     * @return 是否更新成功
     */
    @Override
    public boolean active(Long id) {
        Instance instance = getById(id);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        AssertUtil.isTrue(ActivityStatus.isActivity(instance.getActivityStatus()), ExceptionCons.INSTANCE_ALREADY_ACTIVITY);
        instance.setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        return updateById(instance);
    }

    /**
     * 挂起活动中的流程实例。
     *
     * @param id 流程实例主键
     * @return 是否更新成功
     */
    @Override
    public boolean unActive(Long id) {
        Instance instance = getById(id);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        AssertUtil.isTrue(ActivityStatus.isSuspended(instance.getActivityStatus()), ExceptionCons.INSTANCE_ALREADY_SUSPENDED);
        instance.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        return updateById(instance);
    }

    /**
     * 从实例变量中移除指定键；实例不存在时不执行更新。
     *
     * @param instanceId 流程实例主键
     * @param keys       待移除的变量键
     */
    @Override
    public void removeVariables(Long instanceId, String... keys) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance != null) {
            Map<String, Object> variableMap = instance.getVariableMap();
            for (String key : keys) {
                variableMap.remove(key);
            }
            instance.setVariable(FlowEngine.jsonConvert.objToStr(variableMap));
            FlowEngine.insService().updateById(instance);
        }
    }
}
