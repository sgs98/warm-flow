package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.PathWayData;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.service.event.EventConfig;
import org.dromara.warm.flow.core.service.event.EventMessage;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.service.event.EventPublisher;
import org.dromara.warm.flow.core.timer.TimerContext;
import org.dromara.warm.flow.core.timer.TimerScheduleStrategies;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Collections;

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
     * 分派一批到达节点。
     *
     * @param execution 执行作用域
     * @param nodes     到达节点集合
     * @param skipType  流转类型
     * @return 本批创建的待办任务（含服务任务临时待办，事件节点不产生任务）
     */
    static List<Task> dispatch(FlowExecution execution, List<Node> nodes, String skipType) {
        return dispatch(execution, nodes, skipType, 0);
    }

    /**
     * 分派一批到达节点（内部递归入口）。
     *
     * @param execution 执行作用域
     * @param nodes     到达节点集合
     * @param skipType  流转类型
     * @param depth     递归深度
     * @return 本批创建的待办任务
     */
    private static List<Task> dispatch(FlowExecution execution, List<Node> nodes, String skipType
        , int depth) {
        List<Task> createdTasks = new ArrayList<>();
        if (CollUtil.isEmpty(nodes)) {
            return createdTasks;
        }
        AssertUtil.isTrue(depth > MAX_DISPATCH_DEPTH, ExceptionCons.SERVICE_TASK_LOOP_LIMIT);
        for (Node node : nodes) {
            // 到达即补充该节点的默认流程变量（缺键才写），随后它自己的出口 / 后续网关都能读到
            execution.applyNodeDefaults(node);
            if (StringUtils.isNotEmpty(node.getEventType())) {
                dispatchEventNode(execution, node, skipType, depth, createdTasks);
            } else {
                Task task = FlowEngine.taskService().addTask(node, execution.instance
                    , execution.definition, execution.intent, skipType);
                createdTasks.add(task);
                registerBoundaryWaits(execution, node, task);
            }
        }
        return createdTasks;
    }

    /**
     * 分派单个事件节点。
     */
    private static void dispatchEventNode(FlowExecution execution, Node node, String skipType
        , int depth, List<Task> createdTasks) {
        String eventType = node.getEventType();
        if (EventType.isCatch(eventType)) {
            arriveCatch(execution, node, skipType);
            return;
        }
        if (EventType.isThrow(eventType) || EventType.isEnd(eventType)) {
            // 抛出事件先发布消息；结束事件（MESSAGE_END / TERMINATE_END）同样先完成发布 / 清理
            if (EventType.isThrow(eventType) || EventType.MESSAGE_END.getValue().equals(eventType)) {
                publishMessage(execution, node, null);
            }
            if (EventType.TERMINATE_END.getValue().equals(eventType)) {
                // 终止结束事件：中断实例其它活动等待记录，并把实例终态定为「终止」——
                // 经 intent 传给收口链，随固定 END 的收口写入实例状态
                FlowEngine.eventWaitService().cancelByInstanceIds(List.of(execution.instance.getId()));
                execution.intent.setFlowStatus(
                    FlowStatusMachine.mapper().instanceStatus(FlowStatus.TERMINATE));
            }
            // 结束事件留痕：历史里记一条「流程由该结束事件节点结束」，
            // 终止结束的实例状态为「终止」，正常结束 / 消息结束仍为「已完成」
            recordEndEvent(execution, node);
            // 沿事件出口继续分派（MESSAGE_END / TERMINATE_END 的唯一出口指向固定 END）；
            // 出口解析与路径元数据共用同一收集器，确保事件节点出口连线一并标记为已完成
            PathWayData exitPathWay = exitPathWay(execution, node);
            List<Node> exitNodes = resolveExit(execution, node, exitPathWay);
            exitPathWay.getTargetNodes().addAll(exitNodes);
            execution.instance.setDefJson(FlowEngine.chartService().skipMetadata(exitPathWay));
            createdTasks.addAll(dispatch(execution, exitNodes, skipType, depth + 1));
            return;
        }
        // 启动事件与边界事件不允许出现在实例级路由中
        throw new IllegalStateException("[" + node.getNodeCode() + "]"
            + ExceptionCons.EVENT_START_TOPOLOGY_MISMATCH);
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

    /**
     * 到达中间捕获事件：幂等创建等待记录、实例位置与状态指向等待节点、执行事件节点监听器。
     *
     * @param execution 执行作用域
     * @param node      到达的捕获事件节点
     * @param skipType  流转类型，用于计算实例状态
     */
    private static void arriveCatch(FlowExecution execution, Node node, String skipType) {
        Instance instance = execution.instance;
        // 幂等：该实例该节点已有活动等待记录时跳过
        boolean exists = StreamUtils.toList(FlowEngine.eventWaitService()
            .getWaitingByInstanceId(instance.getId()), EventWait::getNodeCode).contains(node.getNodeCode());
        if (exists) {
            log.info("实例 {} 节点 {} 已存在等待记录，跳过重复到达", instance.getId(), node.getNodeCode());
            return;
        }

        String eventType = node.getEventType();
        // start 监听器在等待记录创建前执行
        ListenerUtil.executeStart(execution.contextListener(null, node, Collections.emptyList()
            , Collections.emptyList()));

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

        // 实例位置指向正在等待的事件节点（本批无人工待办时不会被 setInsFinishInfo 覆盖），
        // 状态按与建待办相同的规则同步，避免等待期间实例状态停留在发起时的待提交 / 草稿
        instance.setNodeType(node.getNodeType())
            .setNodeCode(node.getNodeCode())
            .setNodeName(node.getNodeName())
            .setFlowStatus(StringUtils.emptyDefault(execution.intent.getFlowStatus()
                , FlowStatusMachine.instanceStatus(node.getNodeType(), skipType)));

        // create 监听器在等待记录创建后执行（task 为空是合法语义）
        ListenerUtil.endCreateListener(execution.contextListener(null, node
            , Collections.emptyList(), Collections.emptyList()));
    }

    /**
     * 任务创建后注册附着在该任务节点上的定时 / 消息边界事件等待。
     */
    private static void registerBoundaryWaits(FlowExecution execution, Node taskNode, Task task) {
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

    /**
     * 发布消息（MESSAGE_THROW / MESSAGE_END）：宿主未注册发布器时告警跳过。
     */
    private static void publishMessage(FlowExecution execution, Node node, EventWait wait) {
        EventPublisher publisher = FlowEngine.eventPublisher();
        if (publisher == null) {
            log.warn("未注册 EventPublisher，消息事件 {}({}) 发布被跳过 node={}"
                , node.getEventType(), node.getEventName(), node.getNodeCode());
            return;
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
        if (wait != null) {
            message.setEventWaitId(wait.getId());
        }
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
