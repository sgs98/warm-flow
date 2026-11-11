package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.enums.*;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.service.event.EventMessage;
import org.dromara.warm.flow.core.service.event.EventPublisher;
import org.dromara.warm.flow.core.timer.EventWaitProcessor;
import org.dromara.warm.flow.core.timer.TimerContext;
import org.dromara.warm.flow.core.timer.TimerScheduleStrategies;
import org.dromara.warm.flow.core.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 统一事件到达分派器：到达某节点后的处理收敛为一个分派函数，
 * 由发起链、办理链、撤回链和自动节点推进器共用，禁止各自实现一份判断。
 * <p>
 * 人工 / 服务任务节点 → 建待办（服务任务临时待办由推进器入队）；
 * 捕获事件节点（{@code TIMER_CATCH} / {@code MESSAGE_CATCH}）→ 创建等待记录、不建 Task、
 * 实例位置与状态指向等待节点；
 * 抛出 / 结束事件节点（{@code MESSAGE_THROW} / {@code MESSAGE_END} / {@code TERMINATE_END}）
 * → 发布消息或清理等待后沿事件出口继续分派；
 * 人工待办创建后注册附着其上的定时 / 消息边界事件等待。
 * <p>
 * 事件节点永不进入 {@code addTask}；幂等：分派前先查该实例该节点是否已有活动等待记录。
 *
 * @author may
 */
final class EventArrivalDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventArrivalDispatcher.class);

    /**
     * 抛出 / 结束事件沿出口继续分派的最大深度，防图环路。
     */
    private static final int MAX_DISPATCH_DEPTH = 10;

    private EventArrivalDispatcher() {
    }

    /**
     * 校验并构造完整递归到达批次，不执行到达副作用。
     */
    static ArrivalPlan prepare(FlowExecution execution, List<Node> nodes, String skipType) {
        return prepare(execution, nodes, skipType, false);
    }

    /**
     * 撤回专用预解析：旧等待尚未取消，但本次到达会整体替换它们。
     * <p>捕获事件按实例 + 节点查重时忽略现存等待，否则重新命中同一捕获节点会被误判为
     * 「已存在等待」而不再构造新等待，随后旧等待又被取消，留下零等待。失败路径仍保留旧等待。</p>
     */
    static ArrivalPlan prepareReplacingWaits(FlowExecution execution, List<Node> nodes, String skipType) {
        return prepare(execution, nodes, skipType, true);
    }

    private static ArrivalPlan prepare(FlowExecution execution, List<Node> nodes, String skipType
        , boolean replacingWaits) {
        ArrivalPlan plan = new ArrivalPlan();
        prepare(execution, nodes, skipType, 0, plan, replacingWaits);
        plan.resolveGroupUsers();
        plan.validateCommitActions();
        return plan;
    }

    /**
     * 执行已验证计划中延迟的事件等待、历史与发布副作用。
     */
    static void commit(ArrivalPlan plan) {
        plan.commit();
    }

    private static void prepare(FlowExecution execution, List<Node> nodes, String skipType, int depth
        , ArrivalPlan plan, boolean replacingWaits) {
        if (CollUtil.isEmpty(nodes)) {
            return;
        }
        AssertUtil.isTrue(depth > MAX_DISPATCH_DEPTH, ExceptionCons.SERVICE_TASK_LOOP_LIMIT);
        for (Node node : nodes) {
            execution.applyNodeDefaults(node);
            if (StringUtils.isNotEmpty(node.getEventType())) {
                prepareEventNode(execution, node, skipType, depth, plan, replacingWaits);
            } else {
                Task task = FlowEngine.taskService().addTask(node, execution.instance
                    , execution.definition, execution.intent, skipType);
                plan.addTask(task);
                plan.deferCommit(() -> registerBoundaryWaits(execution, node, task));
            }
        }
    }

    private static void prepareEventNode(FlowExecution execution, Node node, String skipType, int depth
        , ArrivalPlan plan, boolean replacingWaits) {
        String eventType = node.getEventType();
        if (EventType.isCatch(eventType)) {
            if (!plan.addCatchNode(execution.instance.getId(), node.getNodeCode())) {
                return;
            }
            EventWait wait = prepareCatchWait(execution, node, skipType, replacingWaits);
            if (wait != null) {
                plan.deferCommit(() -> ListenerUtil.executeStart(execution.contextListener(null, node
                    , Collections.emptyList(), Collections.emptyList())));
                plan.deferCommit(() -> {
                    FlowEngine.eventWaitService().save(wait);
                    updateWaitingInstance(execution, node, skipType);
                });
                plan.deferCommit(() -> ListenerUtil.endCreateListener(execution.contextListener(null, node
                    , Collections.emptyList(), Collections.emptyList())));
            }
            return;
        }
        if (EventType.isThrow(eventType) || EventType.isEnd(eventType)) {
            if (EventType.isThrow(eventType) || EventType.MESSAGE_END.getValue().equals(eventType)) {
                plan.validatePublisher(node);
                plan.deferCommit(() -> publishMessage(execution, node));
            }
            if (EventType.TERMINATE_END.getValue().equals(eventType)) {
                plan.deferCommit(() -> {
                    FlowEngine.eventWaitService().cancelByInstanceIds(List.of(execution.instance.getId()));
                    execution.intent.setFlowStatus(
                        FlowStatusMachine.mapper().instanceStatus(FlowStatus.TERMINATE));
                });
            }
            if (EventType.isEnd(eventType)) {
                plan.deferCommit(() -> recordEndEvent(execution, node));
            }
            PathWayData exitPathWay = exitPathWay(execution, node);
            List<Node> exitNodes = resolveExit(execution, node, exitPathWay);
            exitPathWay.getTargetNodes().addAll(exitNodes);
            execution.instance.setDefJson(FlowEngine.chartService().skipMetadata(exitPathWay));
            prepare(execution, exitNodes, skipType, depth + 1, plan, replacingWaits);
            return;
        }
        throw new IllegalStateException("[" + node.getNodeCode() + "]"
            + ExceptionCons.EVENT_START_TOPOLOGY_MISMATCH);
    }

    private static EventWait prepareCatchWait(FlowExecution execution, Node node, String skipType
        , boolean replacingWaits) {
        Long currentWaitId = EventWaitProcessor.currentWaitId();
        boolean exists = !replacingWaits && FlowEngine.eventWaitService()
            .getActiveByInstanceId(execution.instance.getId()).stream()
            .anyMatch(wait -> node.getNodeCode().equals(wait.getNodeCode())
                && !Objects.equals(currentWaitId, wait.getId()));
        if (exists) {
            log.info("实例 {} 节点 {} 已存在等待记录，跳过重复到达", execution.instance.getId(), node.getNodeCode());
            return null;
        }
        Instance instance = execution.instance;
        String eventType = node.getEventType();
        EventWait wait = FlowEngine.newEventWait();
        Date now = new Date();
        FlowEngine.dataFillHandler().idFill(wait);
        EventConfig config = EventConfig.parse(node.getNodeConfig());
        wait.setDefinitionId(instance.getDefinitionId())
            .setInstanceId(instance.getId())
            .setNodeCode(node.getNodeCode())
            .setEventType(eventType)
            .setEventName(node.getEventName())
            .setStatus(EventCons.STATUS_WAITING)
            .setRetryCount(0)
            .setRepeatCount(0)
            .setVersion(0)
            .setCreateTime(now)
            .setUpdateTime(now)
            .setTenantId(instance.getTenantId());
        if (config.hasTimer()) {
            Date nextTriggerAt = TimerScheduleStrategies.nextTriggerAt(config.getTimerType()
                , new TimerContext().setPreviousTriggerAt(null).setCurrentTime(now)
                    .setTriggerInterval(config.getTimerValue()).setTimezone(config.getTimezone()).setRepeatCount(0));
            wait.setTriggerType(config.getTimerType()).setTriggerInterval(config.getTimerValue())
                .setTimezone(config.getTimezone()).setMaxRepeatCount(config.getMaxCount())
                .setNextTriggerAt(nextTriggerAt);
        }
        return wait;
    }

    private static void updateWaitingInstance(FlowExecution execution, Node node, String skipType) {
        Instance instance = execution.instance;
        instance.setNodeType(node.getNodeType()).setNodeCode(node.getNodeCode()).setNodeName(node.getNodeName())
            .setFlowStatus(StringUtils.emptyDefault(execution.intent.getFlowStatus()
                , FlowStatusMachine.instanceStatus(node.getNodeType(), skipType)));
    }

    /**
     * 记录结束事件留痕。
     * <p>
     * 结束事件没有对应的待办，{@code task_id} 以 {@code 0} 作为哨兵值；
     * 状态取 {@link TaskStatus#PASS}（「通过」）——结束事件本质是「这一跳完成了」，
     * 任务维度不再单设「结束」状态；终止结束与消息结束的差别体现在 {@code message} 上。
     *
     * @param execution 执行作用域
     * @param node      结束事件节点
     */
    private static void recordEndEvent(FlowExecution execution, Node node) {
        Date now = new Date();
        boolean terminate = EventType.TERMINATE_END.getValue().equals(node.getEventType());
        HisTask hisTask = FlowEngine.newHisTask()
            .setTaskId(0L)
            .setInstanceId(execution.instance.getId())
            .setDefinitionId(execution.instance.getDefinitionId())
            .setNodeCode(node.getNodeCode())
            .setNodeName(node.getNodeName())
            .setNodeType(node.getNodeType())
            .setTargetNodeCode(node.getNodeCode())
            .setTargetNodeName(node.getNodeName())
            .setApprover(FlowEngine.getFlowConfig().getSystemHandler())
            .setCooperateType(CooperateType.NONE.getKey())
            .setSkipType(SkipType.NONE.getKey())
            .setFlowStatus(FlowStatusMachine.mapper().taskStatus(TaskStatus.PASS))
            .setMessage(terminate ? "流程由该终止结束事件终止" : "流程由该结束事件节点结束")
            .setCreateTime(now)
            .setUpdateTime(now);
        FlowEngine.dataFillHandler().idFill(hisTask);
        FlowEngine.hisTaskService().save(hisTask);
    }

    static void validateBoundaries(FlowExecution execution, List<Task> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return;
        }
        List<Node> boundaries = StreamUtils.filter(execution.loadCombineNoDef().getAllNodes()
            , node -> EventType.isBoundary(node.getEventType()));
        for (Task task : tasks) {
            for (Node boundary : boundaries) {
                if (!task.getNodeCode().equals(boundary.getAttachedToNodeCode())) {
                    continue;
                }
                EventConfig config = EventConfig.parse(boundary.getNodeConfig());
                if (EventType.TIMER_BOUNDARY.getValue().equals(boundary.getEventType()) && !config.hasTimer()) {
                    throw new FlowException("[" + boundary.getNodeCode() + "]" + ExceptionCons.TIMER_EVENT_NO_CONFIG);
                }
                if (config.hasTimer()) {
                    TimerScheduleStrategies.nextTriggerAt(config.getTimerType(), new TimerContext()
                        .setPreviousTriggerAt(null).setCurrentTime(new Date())
                        .setTriggerInterval(config.getTimerValue()).setTimezone(config.getTimezone())
                        .setRepeatCount(0));
                }
            }
        }
    }

    /**
     * 任务创建后注册附着在该任务节点上的定时 / 消息边界事件等待。
     */
    static void registerBoundaryWaits(FlowExecution execution, Node taskNode, Task task) {
        List<Node> boundaries = StreamUtils.filter(execution.loadCombineNoDef().getAllNodes()
            , node -> EventType.isBoundary(node.getEventType())
                && taskNode.getNodeCode().equals(node.getAttachedToNodeCode()));
        if (CollUtil.isEmpty(boundaries)) {
            return;
        }
        Date now = new Date();
        for (Node boundary : boundaries) {
            EventConfig config = EventConfig.parse(boundary.getNodeConfig());
            EventWait wait = FlowEngine.newEventWait();
            FlowEngine.dataFillHandler().idFill(wait);
            wait.setDefinitionId(execution.instance.getDefinitionId())
                .setInstanceId(execution.instance.getId())
                .setNodeCode(boundary.getNodeCode())
                .setEventType(boundary.getEventType())
                .setEventName(boundary.getEventName())
                .setAttachedTaskId(task.getId())
                .setStatus(EventCons.STATUS_WAITING)
                .setRetryCount(0)
                .setRepeatCount(0)
                .setVersion(0)
                .setCreateTime(now)
                .setUpdateTime(now)
                .setTenantId(execution.instance.getTenantId());
            if (EventType.TIMER_BOUNDARY.getValue().equals(boundary.getEventType())) {
                AssertUtil.isTrue(!config.hasTimer(), ExceptionCons.TIMER_EVENT_NO_CONFIG);
                Date nextTriggerAt = TimerScheduleStrategies.nextTriggerAt(config.getTimerType()
                    , new TimerContext()
                        .setPreviousTriggerAt(null)
                        .setCurrentTime(now)
                        .setTriggerInterval(config.getTimerValue())
                        .setTimezone(config.getTimezone())
                        .setRepeatCount(0));
                wait.setTriggerType(config.getTimerType())
                    .setTriggerInterval(config.getTimerValue())
                    .setTimezone(config.getTimezone())
                    .setMaxRepeatCount(config.getMaxCount())
                    .setNextTriggerAt(nextTriggerAt);
            }
            FlowEngine.eventWaitService().save(wait);
        }
    }

    static void validatePublisher(Node node) {
        if (FlowEngine.eventPublisher() == null) {
            throw new FlowException("[" + node.getNodeCode() + "]" + ExceptionCons.EVENT_PUBLISHER_MISSING);
        }
    }

    /**
     * 发布消息（MESSAGE_THROW / MESSAGE_END）：宿主未注册发布器时视为发布失败。
     * <p>
     * 抛出 / 结束路径本身没有等待记录，{@code eventWaitId} 为空；宿主去重可用自身业务幂等键
     * （设计 §9.3：可靠投递由宿主 Outbox 承担，发布失败随调用方事务失败）。据此，已配置的
     * 消息抛出 / 消息结束事件在发布器缺失时抛出异常，随调用方事务整体回滚，
     * 不允许「流程照常推进、消息静默丢失」。
     */
    private static void publishMessage(FlowExecution execution, Node node) {
        EventPublisher publisher = FlowEngine.eventPublisher();
        if (publisher == null) {
            log.error("未注册 EventPublisher，消息事件 {}({}) 发布失败 node={}"
                , node.getEventType(), node.getEventName(), node.getNodeCode());
            throw new FlowException("[" + node.getNodeCode() + "]"
                + ExceptionCons.EVENT_PUBLISHER_MISSING);
        }
        EventMessage message = new EventMessage()
            .setEventType(node.getEventType())
            .setEventName(node.getEventName())
            .setDefinitionId(execution.definition.getId())
            .setInstanceId(execution.instance != null ? execution.instance.getId() : null)
            .setNodeCode(node.getNodeCode())
            .setTenantId(execution.instance != null ? execution.instance.getTenantId() : null)
            .setPayload(execution.intent != null ? execution.intent.getVariables() : null)
            .setTriggerTime(new Date());
        publisher.publish(message);
    }

    /**
     * 解析事件节点的普通出口节点，出口连线随传入的路径收集器一并记录。
     */
    private static List<Node> resolveExit(FlowExecution execution, Node node, PathWayData pathWayData) {
        Map<String, Object> variables = execution.intent != null ? execution.intent.getVariables() : null;
        pathWayData.setDefId(node.getDefinitionId()).setSkipType(SkipType.PASS.getKey());
        return FlowEngine.nodeService().getNextNodeList(node, null, SkipType.PASS.getKey()
            , variables, pathWayData, execution.loadCombineNoDef());
    }

    /**
     * 构造事件出口跳转的路径元数据（目标节点由调用方解析后补充）。
     */
    private static PathWayData exitPathWay(FlowExecution execution, Node node) {
        return new PathWayData()
            .setInsId(execution.instance != null ? execution.instance.getId() : null)
            .setSkipType(SkipType.PASS.getKey())
            // 以内存中的最新实例快照为合并基底：终止结束/消息结束分派发生在实例持久化之前，
            // 回库加载会拿到旧 defJson，覆盖本次办理链已写入的节点状态
            .setDefJson(execution.instance != null ? execution.instance.getDefJson() : null)
            .setPathWayNodes(new ArrayList<>(Collections.singletonList(node)));
    }

    /**
     * 判断节点是否为普通人工 / 服务节点（无事件属性）。
     */
    static boolean isPlainNode(Node node) {
        return StringUtils.isEmpty(node.getEventType());
    }

    /**
     * 判断节点是否为可等待的捕获事件节点。
     */
    static boolean isCatchNode(Node node) {
        return EventType.isCatch(node.getEventType());
    }

    /**
     * 判断节点类型是否为事件承载的中间节点（BETWEEN）。
     */
    static boolean isEventBetweenNode(Node node) {
        return NodeType.isBetween(node.getNodeType()) && StringUtils.isNotEmpty(node.getEventType());
    }
}
