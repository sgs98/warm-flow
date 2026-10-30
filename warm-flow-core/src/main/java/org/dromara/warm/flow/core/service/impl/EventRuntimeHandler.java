package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.timer.EventCancelException;
import org.dromara.warm.flow.core.timer.EventSkipException;
import org.dromara.warm.flow.core.timer.EventTaskHandler;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件运行时默认处理器：定时 / 消息等待记录触发后的真实流程推进。
 * <p>
 * 实现定时器底座的 {@link EventTaskHandler} 扩展点，按事件类型分派：
 * <ul>
 * <li>{@code TIMER_START}：校验定义仍发布且激活、拓扑仍匹配，按确定性 businessId
 * 幂等创建实例；</li>
 * <li>{@code TIMER_CATCH} / {@code MESSAGE_CATCH} / {@code ERROR_CATCH}：校验实例激活后沿事件出口推进；</li>
 * <li>{@code TIMER_BOUNDARY} / {@code MESSAGE_BOUNDARY} / {@code ERROR_BOUNDARY}：中断附着任务
 * （归档 {@code INVALID}）、取消同任务其它边界等待、把被中断节点在流程图元数据中补记为已办理，
 * 随后沿边界出口推进。</li>
 * </ul>
 * 抢占后发现定义失效、实例挂起等暂不可执行场景抛 {@link EventSkipException}，
 * 由调度底座释放租约恢复等待，不计入失败重试。宿主可在 starter 中用带事务的实现
 * 覆盖本处理器（core 只调度，宿主持事务）。
 *
 * @author may
 */
public class EventRuntimeHandler implements EventTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(EventRuntimeHandler.class);

    @Override
    public void handle(EventWait wait) {
        handle(wait, null);
    }

    @Override
    public void handle(EventWait wait, WorkflowContext caller) {
        String eventType = wait.getEventType();
        if (EventType.TIMER_START.getValue().equals(eventType)) {
            timerStart(wait);
            return;
        }
        if (EventType.TIMER_CATCH.getValue().equals(eventType)
            || EventType.MESSAGE_CATCH.getValue().equals(eventType)
            || EventType.ERROR_CATCH.getValue().equals(eventType)) {
            catchTrigger(wait, caller);
            return;
        }
        if (EventType.TIMER_BOUNDARY.getValue().equals(eventType)) {
            boundaryTrigger(wait, EventCons.TIMER_BOUNDARY_MESSAGE, caller);
            return;
        }
        if (EventType.MESSAGE_BOUNDARY.getValue().equals(eventType)) {
            boundaryTrigger(wait, EventCons.MESSAGE_BOUNDARY_MESSAGE, caller);
            return;
        }
        if (EventType.ERROR_BOUNDARY.getValue().equals(eventType)) {
            boundaryTrigger(wait, EventCons.ERROR_BOUNDARY_MESSAGE, caller);
            return;
        }
        throw new IllegalStateException("[" + wait.getNodeCode() + "]事件类型不支持运行时触发: " + eventType);
    }

    /**
     * 定时启动事件触发：创建流程实例。
     */
    private void timerStart(EventWait wait) {
        Definition definition = FlowEngine.defService().getById(wait.getDefinitionId());
        if (definition == null || !PublishStatus.PUBLISHED.getKey().equals(definition.getIsPublish())) {
            cancelStale(wait, "定义已不处于发布状态");
            return;
        }
        if (ActivityStatus.isSuspended(definition.getActivityStatus())) {
            cancelStale(wait, "定义已挂起");
            return;
        }
        WorkflowContext context = executionContext(wait, null);
        // 定时启动没有发起人提交环节：实例直接处于「审批中」，而不是「待提交」；
        // taskStatus 给 PASS 让发起事件的历史仍记「通过」，flowStatus 只影响实例状态
        context.setFlowStatus(FlowStatusMachine.mapper().instanceStatus(FlowStatus.WAITING));
        context.setTaskStatus(FlowStatusMachine.mapper().taskStatus(TaskStatus.PASS));
        FlowExecution execution;
        try {
            execution = FlowExecution.loadTimerStart(wait.getDefinitionId(), wait.getNodeCode(), context);
        } catch (FlowExecution.TimerStartTopologyException ex) {
            // 拓扑与等待记录不匹配：取消记录并记录原因，不计入失败重试
            cancelStale(wait, "启动事件拓扑校验失败: " + ex.getMessage());
            return;
        }

        // 确定性 businessId：同一等待记录的同一次计划触发失败重试时复用
        String businessId = timerStartBusinessId(wait);
        List<Instance> existing = FlowEngine.insService()
            .list(FlowEngine.newIns().setDefinitionId(wait.getDefinitionId())
                .setBusinessId(businessId));
        if (existing.size() > 0) {
            log.info("定时启动事件 businessId={} 已存在实例，跳过重复创建", businessId);
            return;
        }

        new FlowStartChain((InsServiceImpl) FlowEngine.insService(), businessId).pipeline().run(execution);
    }

    /**
     * 中间捕获事件触发：实例激活校验后沿事件出口推进。
     * <p>
     * 调度底座是 at-least-once：推进已提交而等待记录的完成标记丢失（执行线程崩溃、
     * 租约过期恢复）后会重放本方法。实例图元数据中事件节点已标「已办理」即重放
     * （到达时标「待办」、推进后标「已办理」、退回重走会重置回「未完成」），取消遗留
     * 记录而非二次推进。定时启动按确定性 businessId、边界事件按附着任务已归档各自幂等。
     */
    private void catchTrigger(EventWait wait, WorkflowContext caller) {
        Instance instance = FlowEngine.insService().getById(wait.getInstanceId());
        if (instance == null) {
            cancelStale(wait, "实例不存在");
            return;
        }
        if (!ActivityStatus.isActivity(instance.getActivityStatus())) {
            // 挂起实例不触发：释放抢占恢复等待，实例恢复后由扫描器补发
            throw new EventSkipException("实例已挂起，暂停触发");
        }
        if (alreadyPassed(instance, wait.getNodeCode())) {
            cancelStale(wait, "实例已越过事件节点，等待记录不再有效");
            return;
        }
        WorkflowContext context = executionContext(wait, caller);
        FlowExecution execution = loadEventExecution(instance, wait.getNodeCode(), context);
        Node eventNode = execution.nowNode;
        ListenerUtil.executeStart(execution.contextListener(null, eventNode));
        EventFlowAdvancer.advanceFromEvent(execution, eventNode);
    }

    /**
     * 边界事件触发：中断附着任务后沿边界出口推进。
     */
    private void boundaryTrigger(EventWait wait, String message, WorkflowContext caller) {
        Task task = FlowEngine.taskService().getById(wait.getAttachedTaskId());
        if (task == null) {
            // 附着任务已完成或已不存在：取消等待，不再触发
            cancelStale(wait, "附着任务已不存在");
            return;
        }
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        if (instance == null) {
            cancelStale(wait, "实例不存在");
            return;
        }
        if (!ActivityStatus.isActivity(instance.getActivityStatus())) {
            throw new EventSkipException("实例已挂起，暂停触发");
        }
        WorkflowContext context = executionContext(wait, caller);
        FlowExecution execution = loadEventExecution(instance, wait.getNodeCode(), context);
        Node eventNode = execution.nowNode;

        // 被中断任务对应的宿主节点：随本次流程图元数据补记为已办理，避免残留「待办理」
        Node interruptedNode = StreamUtils.filterOne(execution.loadCombineNoDef().getAllNodes()
            , node -> node.getNodeCode().equals(task.getNodeCode()));

        // 中断原任务：归档为 INVALID（不代表审批人同意），沿边界出口继续
        interruptAttachedTask(execution, task, eventNode, message);

        // 取消同任务的其它边界事件等待；当前执行记录由调度器在完成阶段处理。
        FlowEngine.eventWaitService().cancelByAttachedTaskId(task.getId());

        ListenerUtil.executeStart(execution.contextListener(null, eventNode));
        EventFlowAdvancer.advanceFromEvent(execution, eventNode, interruptedNode);
    }

    /**
     * 中断附着任务：删除待办与办理人，归档历史任务为 INVALID / PASS。
     * INVALID 仅用于本次归档，随后复位调用方上下文状态，避免泄漏到后续推进。
     */
    private void interruptAttachedTask(FlowExecution execution, Task task, Node eventNode
        , String message) {
        WorkflowContext intent = execution.intent;
        intent.setMessage(message);
        intent.setTaskStatus(FlowStatusMachine.mapper().taskStatus(TaskStatus.INVALID));
        HisTask hisTask = FlowEngine.hisTaskService().setSkipInsHis(task
            , Collections.singletonList(eventNode), intent, SkipType.PASS.getKey());
        FlowEngine.hisTaskService().save(hisTask);
        // 复位上下文状态：后续沿边界出口的推进由状态机按目标节点决定
        intent.setTaskStatus(null);
        TaskServiceImpl taskService = (TaskServiceImpl) FlowEngine.taskService();
        taskService.removeAndUserInternal(Collections.singletonList(task));
    }

    /**
     * 加载事件触发执行作用域：实例、定义、定义图与事件节点。
     */
    private FlowExecution loadEventExecution(Instance instance, String nodeCode, WorkflowContext context) {
        return FlowExecution.loadForEvent(instance, nodeCode, context);
    }

    /**
     * 构造事件执行的上下文：调用方显式指定的操作人 / 租户优先，未指定时回退
     * 系统兜底（{@code systemHandler} / 等待记录租户）；payload 合并为流程变量。
     */
    private WorkflowContext executionContext(EventWait wait, WorkflowContext caller) {
        WorkflowContext context = WorkflowContext.create(caller.getHandler());
        context.setTenantId(caller != null && StringUtils.isNotEmpty(caller.getTenantId())
            ? caller.getTenantId() : wait.getTenantId());
        context.setIgnore(true);
        Map<String, Object> merged = caller != null && caller.getVariables() != null
            ? new HashMap<>(caller.getVariables()) : new HashMap<>();
        Map<String, Object> payload = FlowEngine.jsonConvert.strToMap(wait.getPayload());
        if (payload != null) {
            merged.putAll(payload);
        }
        if (!merged.isEmpty()) {
            context.setVariables(merged);
        }
        return context;
    }

    /**
     * 定时启动确定性业务标识：{@code T{eventWaitId}_{plannedTriggerAtMillis}}。
     */
    private String timerStartBusinessId(EventWait wait) {
        long planned = wait.getNextTriggerAt() != null ? wait.getNextTriggerAt().getTime() : 0L;
        return "T" + wait.getId() + "_" + planned;
    }

    /**
     * 取消遗留等待记录并记录原因，随后跳过本次执行（不计入失败重试）。
     * <p>
     * 抛出取消信号，让事件事务先回滚，再由调度器在事务外条件取消该记录。
     */
    private void cancelStale(EventWait wait, String reason) {
        log.warn("事件等待记录已失效，取消执行 id={} eventType={} reason={}"
            , wait.getId(), wait.getEventType(), reason);
        throw new EventCancelException(reason);
    }

    /**
     * 判断实例图元数据中事件节点是否已被标记为「已办理」。
     * <p>
     * 到达捕获事件节点时节点被标为「待办」，沿出口推进后标为「已办理」；退回重走会把
     * 后续路径重置回「未完成」、再次到达时重新标为「待办」。因此「已办理」意味着实例
     * 已越过该事件节点且未再到达。并行分支下实例位置字段指向任务分支，不能用位置等值
     * 判断，图元数据才是各分支独立的完成事实。
     *
     * @param instance 流程实例
     * @param nodeCode 事件节点编码
     * <p>
     * 包内共享：等待记录复活前也用同一判定，避免复活后去推进一个已经办结的节点。
     *
     * @return 已办理返回 {@code true}；图元数据缺失或节点未找到返回 {@code false}（放行，由既有链路兜底）
     */
    static boolean alreadyPassed(Instance instance, String nodeCode) {
        if (StringUtils.isEmpty(instance.getDefJson())) {
            return false;
        }
        DefJson defJson = FlowEngine.jsonConvert.strToBean(instance.getDefJson(), DefJson.class);
        if (defJson == null) {
            return false;
        }
        NodeJson nodeJson = StreamUtils.filterOne(defJson.getNodeList()
            , item -> nodeCode.equals(item.getNodeCode()));
        return nodeJson != null && ChartStatus.isDone(nodeJson.getStatus());
    }
}
