package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.config.WarmFlow;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.dto.DefJson;
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
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.TaskService;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingServiceTaskHandler;
import org.dromara.warm.flow.core.timer.EventWaitProcessor;
import org.dromara.warm.flow.core.timer.TimerClaim;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事件运行时（阶段 C / D）特征测试：定时启动幂等创建实例、中间捕获等待与推进、
 * 定时边界中断与临时待办边界清理、消息启动与捕获、等待实例的撤回与终止、定义级等待生命周期。
 *
 * @author may
 */
class EventRuntimeCharacteristicTest {

    /**
     * 定时事件配置：延迟 1 小时。
     */
    private static final String TIMER_CONFIG = "{\"event\":{\"timerType\":\"AFTER\",\"timerValue\":\"PT1H\"}}";

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        harness.register(org.dromara.warm.flow.core.timer.EventTaskHandler.class, new EventRuntimeHandler());
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void timerStartPublishCreatesWaitAndTriggerCreatesInstance() {
        Definition def = timerStartFlow("evStart");
        FlowEngine.defService().publish(def.getId());

        // 发布钩子创建定义级等待记录
        List<EventWait> waits = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setDefinitionId(def.getId()));
        assertEquals(1, waits.size());
        EventWait wait = waits.get(0);
        assertEquals(EventType.TIMER_START.getValue(), wait.getEventType());
        assertNull(wait.getInstanceId());
        assertNotNull(wait.getNextTriggerAt());

        // 手动启动被拒绝
        assertThrows(FlowException.class, () -> FlowEngine.insService()
            .start("biz-manual", "evStart", TestFlows.context(TestFlows.HANDLER)));

        // 触发：CAS 抢占 + 处理，按确定性 businessId 创建实例
        assertTrue(new TimerClaim(harness.eventWaitDao).claim(wait
            , new Date(wait.getNextTriggerAt().getTime() + 1000L)));
        new EventWaitProcessor(harness.eventWaitDao, FlowEngine.getFlowConfig())
            .process(wait, wait.getStatus(), wait.getNextTriggerAt(), wait.getRetryAt());

        List<Instance> instances = FlowEngine.insService().getByDefId(def.getId());
        assertEquals(1, instances.size());
        Instance instance = instances.get(0);
        assertEquals("T" + wait.getId() + "_" + wait.getNextTriggerAt().getTime(), instance.getBusinessId());
        assertEquals("apply", TestFlows.currentTask(instance.getId()).getNodeCode());
        // 定时启动没有发起人提交环节：实例直接处于审批中，而非待提交
        assertEquals(FlowStatus.APPROVAL.getKey(), instance.getFlowStatus());
        // 一次性事件成功后删除等待记录
        assertTrue(FlowEngine.eventWaitService().list(FlowEngine.newEventWait()
            .setDefinitionId(def.getId())).isEmpty());

        // 幂等：流程推进成功但完成标记丢失（模拟崩溃）→ 租约恢复重试 →
        // 同一等待记录同一计划触发复用相同 businessId，不重复创建实例
        EventWait stale = FlowEngine.newEventWait().setId(wait.getId())
            .setDefinitionId(def.getId()).setNodeCode(wait.getNodeCode())
            .setEventType(EventType.TIMER_START.getValue())
            .setStatus(EventCons.STATUS_WAITING)
            .setTriggerType(wait.getTriggerType())
            .setTriggerInterval(wait.getTriggerInterval())
            .setNextTriggerAt(wait.getNextTriggerAt())
            .setRepeatCount(0).setRetryCount(0).setVersion(wait.getVersion());
        harness.eventWaitDao.save(stale);
        assertTrue(new TimerClaim(harness.eventWaitDao).claim(stale
            , new Date(stale.getNextTriggerAt().getTime() + 1000L)));
        new EventRuntimeHandler().handle(stale);
        assertEquals(1, FlowEngine.insService().getByDefId(def.getId()).size());
        new EventWaitProcessor(harness.eventWaitDao, FlowEngine.getFlowConfig())
            .process(stale, EventCons.STATUS_WAITING, stale.getNextTriggerAt(), null);
        assertEquals(1, FlowEngine.insService().getByDefId(def.getId()).size());
        assertTrue(FlowEngine.eventWaitService().list(FlowEngine.newEventWait()
            .setDefinitionId(def.getId())).isEmpty());
    }

    @Test
    void timerCatchWaitsThenAdvancesToFinish() {
        Definition def = timerCatchFlow("evCatch");
        Instance instance = FlowEngine.insService().start("biz-catch", "evCatch"
            , TestFlows.context(TestFlows.HANDLER));
        assertEquals("apply", TestFlows.currentTask(instance.getId()).getNodeCode());

        // 办理后到达中间捕获事件：不建普通待办，实例位置指向事件节点
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
        EventWait wait = singleWaiting(instance.getId());
        assertEquals("tc", wait.getNodeCode());
        assertNotNull(wait.getNextTriggerAt());
        Instance stored = harness.insDao.raw(instance.getId());
        assertEquals("tc", stored.getNodeCode());
        // 无人工待办的事件等待同样要推进实例状态，不能停留在发起时的待提交
        assertEquals(FlowStatus.APPROVAL.getKey(), stored.getFlowStatus()
            , "到达捕获事件等待时实例状态应推进为审批中");
        assertFalse(FlowEngine.workflow().waitingEvents(instance.getId()).isEmpty());

        // 到期触发：沿事件出口推进至结束，等待记录删除
        claimAndProcess(wait);
        Instance finished = harness.insDao.raw(instance.getId());
        assertEquals("end", finished.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), finished.getFlowStatus());
        assertTrue(FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId()).isEmpty());
    }

    @Test
    void boundaryTimerInterruptsTaskWhenTimerFirst() {
        Definition def = boundaryFlow("evBoundary");
        Instance instance = FlowEngine.insService().start("biz-bd", "evBoundary"
            , TestFlows.context(TestFlows.HANDLER));
        Task apply = TestFlows.currentTask(instance.getId());
        EventWait boundary = singleWaiting(instance.getId());
        assertEquals(apply.getId(), boundary.getAttachedTaskId());

        claimAndProcess(boundary);

        // 原任务中断：待办删除，归档为 INVALID（不代表审批人同意）
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
        HisTask his = hisOf(instance.getId(), "apply");
        assertEquals(TaskStatus.INVALID.getKey(), his.getFlowStatus());
        assertTrue(his.getMessage().contains("定时边界事件中断"));
        // 沿边界出口推进至结束
        Instance finished = harness.insDao.raw(instance.getId());
        assertEquals("end", finished.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), finished.getFlowStatus());
        assertTrue(FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId()).isEmpty());

        // 流程图元数据：被中断的原节点补记为已办理，不残留「待办理」
        Map<String, Integer> chart = chartStatus(instance.getId());
        assertEquals(ChartStatus.DONE.getKey(), chart.get("apply")
            , "被中断的原节点不应残留「待办理」");
        assertEquals(ChartStatus.DONE.getKey(), chart.get("tb"), "边界事件节点应标记已办理");
        assertEquals(ChartStatus.DONE.getKey(), chart.get("end"), "边界出口直达结束节点");
    }

    @Test
    void taskCompletionCancelsBoundaryWait() {
        Definition def = boundaryFlow("evBoundary2");
        Instance instance = FlowEngine.insService().start("biz-bd2", "evBoundary2"
            , TestFlows.context(TestFlows.HANDLER));
        assertNotNull(singleWaiting(instance.getId()));

        // 任务先完成：边界等待被取消，定时器不再触发
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        List<EventWait> waits = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setInstanceId(instance.getId()));
        assertEquals(1, waits.size());
        assertEquals(EventCons.STATUS_CANCELLED, waits.get(0).getStatus());
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void serviceTempTaskRemovalCancelsBoundaryWait() {
        // 边界事件附着在服务节点上：临时待办由推进器删除时，其边界等待必须一并取消
        boundaryOnServiceFlow("evBoundarySvc");
        Instance instance = FlowEngine.insService().start("biz-bd-svc", "evBoundarySvc"
            , TestFlows.context(TestFlows.HANDLER));

        // 服务节点已自动推进，只留人工节点待办
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("apply", pending.get(0).getNodeCode());

        List<EventWait> waits = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setInstanceId(instance.getId()));
        assertEquals(1, waits.size());
        assertEquals(EventCons.STATUS_CANCELLED, waits.get(0).getStatus());
        assertTrue(FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId()).isEmpty()
            , "临时待办已删除，不能残留活动等待被定时器误触发");
    }

    @Test
    void insRemoveDeletesInstanceEventWaits() {
        // 实例删除时等待事件必须随实例一并清理，不能残留指向已删实例的记录
        boundaryFlow("evBoundaryRm");
        Instance instance = FlowEngine.insService().start("biz-bd-rm", "evBoundaryRm"
            , TestFlows.context(TestFlows.HANDLER));
        assertNotNull(singleWaiting(instance.getId()));

        assertTrue(FlowEngine.insService().remove(List.of(instance.getId())));
        assertEquals(0, harness.insDao.size());
        assertEquals(0, FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setInstanceId(instance.getId())).size());
    }

    @Test
    void removeDefDeletesDefinitionEventWaits() {
        // 定义删除时其定义级等待（定时 / 消息启动）必须一并清理
        Definition def = timerStartFlow("evDefRm");
        FlowEngine.defService().publish(def.getId());
        assertNotNull(singleDefWait(def.getId()));

        assertTrue(FlowEngine.defService().removeDef(List.of(def.getId())));
        assertEquals(0, FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setDefinitionId(def.getId())).size());
    }

    @Test
    void messageStartAndCatchLifecycle() {
        Definition def = messageFlow("evMsg");
        // 手动启动被拒绝
        assertThrows(FlowException.class, () -> FlowEngine.insService()
            .start("biz-m0", "evMsg", TestFlows.context(TestFlows.HANDLER)));

        // 消息启动：payload 合并为流程变量
        Instance instance = FlowEngine.insService().startByMessage("biz-m1", "evMsg", "go"
            , Map.of("orderNo", "A1"), TestFlows.context(TestFlows.HANDLER));
        assertEquals("apply", TestFlows.currentTask(instance.getId()).getNodeCode());
        assertEquals("A1", harness.insDao.raw(instance.getId()).getVariableMap().get("orderNo"));

        // 消息名称不匹配时拒绝启动
        assertThrows(FlowException.class, () -> FlowEngine.insService().startByMessage("biz-m2"
            , "evMsg", "other", null, TestFlows.context(TestFlows.HANDLER)));

        // 办理后等待消息
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        EventWait wait = singleWaiting(instance.getId());
        assertEquals("mc", wait.getNodeCode());
        assertEquals("kick", wait.getEventName());
        assertNull(wait.getNextTriggerAt());
        // 消息等待期间实例状态同样推进为审批中
        assertEquals(FlowStatus.APPROVAL.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());

        // 消息触发推进至结束
        Instance after = FlowEngine.insService().triggerMessage(instance.getId(), "kick"
            , Map.of("result", "ok"), TestFlows.context(TestFlows.HANDLER));
        assertEquals(FlowStatus.FINISHED.getKey(), after.getFlowStatus());
        // 触发时传入的 payload 合并为流程变量（回归守护：曾出现消息载荷被静默丢弃）
        assertEquals("ok", harness.insDao.raw(instance.getId()).getVariableMap().get("result"));
        assertTrue(FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId()).isEmpty());

        // 完成后再次触发同一消息：无匹配等待记录，报错
        assertThrows(FlowException.class, () -> FlowEngine.insService().triggerMessage(instance.getId()
            , "kick", null, TestFlows.context(TestFlows.HANDLER)));
    }

    @Test
    void messageTriggerBindsPayloadToWaitForRetry() {
        messageFlow("evMsgBind");
        Instance instance = FlowEngine.insService()
            .startByMessage("biz-mb", "evMsgBind", "go", null, TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        EventWait wait = singleWaiting(instance.getId());

        // 载荷按 id + version 绑定到等待记录，且不递增版本号（供重试 / 租约恢复读取）
        String payloadJson = "{\"result\":\"bound\"}";
        assertTrue(FlowEngine.eventWaitService().bindPayload(wait.getId(), wait.getVersion(), payloadJson));
        assertEquals(payloadJson, harness.eventWaitDao.raw(wait.getId()).getPayload());
        assertEquals(wait.getVersion(), harness.eventWaitDao.raw(wait.getId()).getVersion());
        // 版本不匹配时绑定失败
        assertFalse(FlowEngine.eventWaitService()
            .bindPayload(wait.getId(), wait.getVersion() + 1, "{}"));

        // 触发仍按原版本抢占成功，载荷合并为流程变量
        Instance after = FlowEngine.insService().triggerMessage(instance.getId(), "kick"
            , Map.of("result", "bound"), TestFlows.context(TestFlows.HANDLER));
        assertEquals(FlowStatus.FINISHED.getKey(), after.getFlowStatus());
        assertEquals("bound", harness.insDao.raw(instance.getId()).getVariableMap().get("result"));
    }

    @Test
    void messageBoundaryCarriesCallerContextToAudit() {
        messageBoundaryFlow("evMsgBd");
        Instance instance = FlowEngine.insService().start("biz-mbd", "evMsgBd"
            , TestFlows.context(TestFlows.HANDLER));
        Task apply = TestFlows.currentTask(instance.getId());
        EventWait boundary = singleWaiting(instance.getId());
        assertEquals(apply.getId(), boundary.getAttachedTaskId());

        // 消息边界由调用方触发：审计操作人取调用方 handler，而不是系统兜底
        FlowEngine.insService().triggerMessage(instance.getId(), "cancel", null
            , TestFlows.context("approver-x"));

        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
        HisTask his = hisOf(instance.getId(), "apply");
        assertEquals(TaskStatus.INVALID.getKey(), his.getFlowStatus());
        assertEquals("approver-x", his.getApprover());
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        assertEquals(ChartStatus.DONE.getKey(), chartStatus(instance.getId()).get("apply")
            , "被中断的原节点不应残留「待办理」");
    }

    @Test
    void errorCatchTriggerAdvancesFlow() {
        errorCatchFlow("evErr", "sys_err");
        Instance instance = FlowEngine.insService().start("biz-err", "evErr"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        // 到达错误捕获：不建普通待办，等待记录无定时字段（错误不参与调度）
        EventWait wait = singleWaiting(instance.getId());
        assertEquals("ec", wait.getNodeCode());
        assertEquals("sys_err", wait.getEventName());
        assertNull(wait.getNextTriggerAt());
        assertEquals("ec", harness.insDao.raw(instance.getId()).getNodeCode());

        // 错误码不匹配：抛出未捕获异常，实例与等待记录保持原状
        assertThrows(FlowException.class, () -> FlowEngine.insService()
            .throwError(instance.getId(), "other", "boom"));
        assertEquals("ec", harness.insDao.raw(instance.getId()).getNodeCode());
        assertEquals(EventCons.STATUS_WAITING, singleWaiting(instance.getId()).getStatus());

        // 精确匹配：沿错误出口推进至结束，等待记录删除
        assertTrue(FlowEngine.insService().throwError(instance.getId(), "sys_err", "boom"));
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        assertTrue(FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId()).isEmpty());

        // 完成后再次抛出同一错误：无匹配等待记录，报错
        assertThrows(FlowException.class, () -> FlowEngine.insService()
            .throwError(instance.getId(), "sys_err", null));
    }

    @Test
    void errorCatchWildcardMatchesAnyCode() {
        errorCatchFlow("evErrWild", null);
        Instance instance = FlowEngine.insService().start("biz-errw", "evErrWild"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        EventWait wait = singleWaiting(instance.getId());
        assertNull(wait.getEventName());

        // 通配捕获：任意错误码均可触发
        assertTrue(FlowEngine.insService().throwError(instance.getId(), "any_code", null));
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void errorBoundaryInterruptsTask() {
        errorBoundaryFlow("evErrBd");
        Instance instance = FlowEngine.insService().start("biz-errbd", "evErrBd"
            , TestFlows.context(TestFlows.HANDLER));
        Task apply = TestFlows.currentTask(instance.getId());
        EventWait boundary = singleWaiting(instance.getId());
        assertEquals(apply.getId(), boundary.getAttachedTaskId());

        // 错误码不匹配：任务与边界等待均保持原状
        assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .throwError(apply.getId(), "other", "boom"));
        assertEquals(apply.getId(), TestFlows.currentTask(instance.getId()).getId());
        assertEquals(EventCons.STATUS_WAITING, singleWaiting(instance.getId()).getStatus());

        // 精确匹配：中断任务（归档 INVALID）并沿错误出口推进至结束
        assertTrue(FlowEngine.taskService().throwError(apply.getId(), "sys_err", "boom"));
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
        HisTask his = hisOf(instance.getId(), "apply");
        assertEquals(TaskStatus.INVALID.getKey(), his.getFlowStatus());
        assertTrue(his.getMessage().contains("错误边界事件中断"));
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        assertTrue(FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId()).isEmpty());
        assertEquals(ChartStatus.DONE.getKey(), chartStatus(instance.getId()).get("apply")
            , "被中断的原节点不应残留「待办理」");
    }

    @Test
    void errorUncaughtWithoutAnyWaitKeepsInstanceState() {
        errorCatchFlow("evErrNone", "sys_err");
        Instance instance = FlowEngine.insService().start("biz-errn", "evErrNone"
            , TestFlows.context(TestFlows.HANDLER));

        // 实例尚无任何错误等待（还在人工任务上）：实例级抛错未捕获，实例状态不变
        assertThrows(FlowException.class, () -> FlowEngine.insService()
            .throwError(instance.getId(), "sys_err", null));
        assertEquals("apply", TestFlows.currentTask(instance.getId()).getNodeCode());
        assertTrue(FlowEngine.eventWaitService().getWaitingByInstanceId(instance.getId()).isEmpty());
    }

    @Test
    void revokeWaitingInstanceReRoutesFromStart() {
        timerCatchFlow("evRevoke");
        Instance instance = FlowEngine.insService().start("biz-rv", "evRevoke"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        assertNotNull(singleWaiting(instance.getId()));

        // 撤回：先取消实例所有活动等待，再从固定开始节点重新路由
        FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER));
        assertEquals("apply", TestFlows.currentTask(instance.getId()).getNodeCode());
        List<EventWait> waits = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setInstanceId(instance.getId()));
        assertEquals(1, waits.size());
        assertEquals(EventCons.STATUS_CANCELLED, waits.get(0).getStatus());
    }

    @Test
    void terminateWaitingInstanceWithoutTask() {
        timerCatchFlow("evTerm");
        Instance instance = FlowEngine.insService().start("biz-tm", "evTerm"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        assertNotNull(singleWaiting(instance.getId()));

        // 无普通待办的等待实例：实例级终止，取消所有等待事件
        Instance terminated = FlowEngine.taskService().terminateByInstanceId(instance.getId()
            , TestFlows.context(TestFlows.HANDLER));
        assertEquals("end", terminated.getNodeCode());
        assertEquals(FlowStatus.TERMINATE.getKey(), terminated.getFlowStatus());
        List<EventWait> waits = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setInstanceId(instance.getId()));
        assertEquals(1, waits.size());
        assertEquals(EventCons.STATUS_CANCELLED, waits.get(0).getStatus());
    }

    @Test
    void definitionSuspendActivatesSyncTimerStartWait() {
        Definition def = timerStartFlow("evSuspend");
        FlowEngine.defService().publish(def.getId());
        assertNotNull(singleDefWait(def.getId()));

        // 定义挂起取消 Timer Start 等待；重新激活后重建并从激活时间重排期
        FlowEngine.defService().unActive(def.getId());
        List<EventWait> suspended = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setDefinitionId(def.getId()));
        assertEquals(1, suspended.size());
        assertEquals(EventCons.STATUS_CANCELLED, suspended.get(0).getStatus());

        FlowEngine.defService().active(def.getId());
        List<EventWait> reactivated = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setDefinitionId(def.getId()));
        boolean hasWaiting = reactivated.stream()
            .anyMatch(wait -> EventCons.STATUS_WAITING.equals(wait.getStatus()));
        assertTrue(hasWaiting, "重新激活后应重建 WAITING 状态的定时启动等待");
    }

    /**
     * 读取实例流程图快照中的节点状态。
     *
     * @param instanceId 流程实例 id
     * @return 节点编码到流程图状态的映射
     */
    private Map<String, Integer> chartStatus(Long instanceId) {
        DefJson defJson = FlowEngine.jsonConvert
            .strToBean(harness.insDao.raw(instanceId).getDefJson(), DefJson.class);
        return StreamUtils.toMap(defJson.getNodeList(), NodeJson::getNodeCode, NodeJson::getStatus);
    }

    /**
     * 抢占并同步处理一条等待记录（与调度底座完成语义一致）；
     * 按记录自身到期时间判定抢占时刻，测试无需真实等待 1 小时。
     */
    private void claimAndProcess(EventWait wait) {
        Date due = wait.getNextTriggerAt() != null
            ? new Date(wait.getNextTriggerAt().getTime() + 1000L) : new Date();
        assertTrue(new TimerClaim(harness.eventWaitDao).claim(wait, due));
        new EventWaitProcessor(harness.eventWaitDao, FlowEngine.getFlowConfig())
            .process(wait, wait.getStatus(), wait.getNextTriggerAt(), wait.getRetryAt());
    }

    /**
     * 取实例唯一处于等待 / 重试状态的记录。
     */
    private EventWait singleWaiting(Long instanceId) {
        List<EventWait> waits = FlowEngine.eventWaitService().getWaitingByInstanceId(instanceId);
        assertEquals(1, waits.size());
        return waits.get(0);
    }

    /**
     * 取定义下唯一等待记录（不限状态）。
     */
    private EventWait singleDefWait(Long definitionId) {
        List<EventWait> waits = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setDefinitionId(definitionId));
        assertEquals(1, waits.size());
        return waits.get(0);
    }

    /**
     * 取实例指定节点的历史任务。
     */
    private HisTask hisOf(Long instanceId, String nodeCode) {
        return harness.hisTaskDao.all().stream()
            .filter(his -> instanceId.equals(his.getInstanceId())
                && nodeCode.equals(his.getNodeCode()))
            .reduce((a, b) -> b)
            .orElseThrow(() -> new AssertionError("缺少历史任务 " + nodeCode));
    }

    @Test
    void timerStartAppliesNodeDefaultVariables() {
        Definition def = timerStartFlow("evStartVar"
            , "{\"variables\":{\"veto\":0,\"channel\":\"daily\"}"
                + ",\"event\":{\"timerType\":\"AFTER\",\"timerValue\":\"PT1M\"}}");
        FlowEngine.defService().publish(def.getId());

        EventWait wait = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setDefinitionId(def.getId())).get(0);
        assertNotNull(wait.getPayload(), "节点默认变量应随等待记录落库");

        assertTrue(new TimerClaim(harness.eventWaitDao).claim(wait
            , new Date(wait.getNextTriggerAt().getTime() + 1000L)));
        new EventWaitProcessor(harness.eventWaitDao, FlowEngine.getFlowConfig())
            .process(wait, wait.getStatus(), wait.getNextTriggerAt(), wait.getRetryAt());

        Instance instance = FlowEngine.insService().getByDefId(def.getId()).get(0);
        Map<String, Object> variables = instance.getVariableMap();
        assertEquals(0, ((Number) variables.get("veto")).intValue(), "默认变量应写入实例");
        assertEquals("daily", variables.get("channel"));
    }

    @Test
    void timerStartStillReadsLegacyEventVariables() {
        // 早期版本把默认变量放在 event 段下，节点级解析需回落兼容，存量 JSON 不用迁移
        Definition def = timerStartFlow("evStartVarLegacy"
            , "{\"event\":{\"timerType\":\"AFTER\",\"timerValue\":\"PT1M\""
                + ",\"variables\":{\"veto\":0,\"channel\":\"daily\"}}}");
        FlowEngine.defService().publish(def.getId());

        EventWait wait = FlowEngine.eventWaitService()
            .list(FlowEngine.newEventWait().setDefinitionId(def.getId())).get(0);
        assertNotNull(wait.getPayload(), "旧格式的默认变量也应随等待记录落库");
    }

    /**
     * 定时启动流程：start → ts(TIMER_START) → apply → end。
     */
    private Definition timerStartFlow(String flowCode) {
        return timerStartFlow(flowCode, TIMER_CONFIG);
    }

    /**
     * 定时启动流程（自定义事件节点配置）。
     */
    private Definition timerStartFlow(String flowCode, String nodeConfig) {
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "ts", NodeType.BETWEEN.getKey(), null, EventType.TIMER_START.getValue()
            , null, null, nodeConfig);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        skip(def, "start", NodeType.START.getKey(), "ts", NodeType.BETWEEN.getKey());
        skip(def, "ts", NodeType.BETWEEN.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 中间捕获流程：start → apply → tc(TIMER_CATCH) → end。
     */
    private Definition timerCatchFlow(String flowCode) {
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "tc", NodeType.BETWEEN.getKey(), null, EventType.TIMER_CATCH.getValue()
            , null, null, TIMER_CONFIG);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "tc", NodeType.BETWEEN.getKey());
        skip(def, "tc", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 边界事件流程：start → apply → end，定时边界 tb 附着在 apply 上，出口指向 end。
     */
    private Definition boundaryFlow(String flowCode) {
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        node(def, "tb", NodeType.BETWEEN.getKey(), null, EventType.TIMER_BOUNDARY.getValue()
            , null, "apply", TIMER_CONFIG);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "tb", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 边界事件附着在服务节点上的流程：start → s1(服务) → apply → end，定时边界 tb 附着在 s1 上。
     */
    private Definition boundaryOnServiceFlow(String flowCode) {
        String serviceConfig = "{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
            + RecordingServiceTaskHandler.class.getName() + "\"}}";
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "s1", NodeType.SERVICE.getKey(), null, null, null, null, serviceConfig);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        node(def, "tb", NodeType.BETWEEN.getKey(), null, EventType.TIMER_BOUNDARY.getValue()
            , null, "s1", TIMER_CONFIG);
        skip(def, "start", NodeType.START.getKey(), "s1", NodeType.SERVICE.getKey());
        skip(def, "s1", NodeType.SERVICE.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "tb", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 消息流程：start → ms(MESSAGE_START, go) → apply → mc(MESSAGE_CATCH, kick) → end。
     */
    private Definition messageFlow(String flowCode) {
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "ms", NodeType.BETWEEN.getKey(), null, EventType.MESSAGE_START.getValue()
            , "go", null, null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "mc", NodeType.BETWEEN.getKey(), null, EventType.MESSAGE_CATCH.getValue()
            , "kick", null, null);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        skip(def, "start", NodeType.START.getKey(), "ms", NodeType.BETWEEN.getKey());
        skip(def, "ms", NodeType.BETWEEN.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "mc", NodeType.BETWEEN.getKey());
        skip(def, "mc", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 消息边界流程：start → apply → end，消息边界 mb(cancel) 附着在 apply 上，出口指向 end。
     */
    private Definition messageBoundaryFlow(String flowCode) {
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        node(def, "mb", NodeType.BETWEEN.getKey(), null, EventType.MESSAGE_BOUNDARY.getValue()
            , "cancel", "apply", null);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "mb", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 错误捕获流程：start → apply → ec(ERROR_CATCH, errorCode) → end。
     * errorCode 为 null 表示通配捕获。
     */
    private Definition errorCatchFlow(String flowCode, String errorCode) {
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "ec", NodeType.BETWEEN.getKey(), null, EventType.ERROR_CATCH.getValue()
            , errorCode, null, null);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "ec", NodeType.BETWEEN.getKey());
        skip(def, "ec", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 错误边界流程：start → apply → end，错误边界 eb(code=sys_err) 附着在 apply 上，出口指向 end。
     */
    private Definition errorBoundaryFlow(String flowCode) {
        Definition def = def(flowCode);
        node(def, "start", NodeType.START.getKey(), null, null, null, null, null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER, null, null, null, null);
        node(def, "end", NodeType.END.getKey(), null, null, null, null, null);
        node(def, "eb", NodeType.BETWEEN.getKey(), null, EventType.ERROR_BOUNDARY.getValue()
            , "sys_err", "apply", null);
        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "eb", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 保存已发布且激活的流程定义骨架。
     */
    private Definition def(String flowCode) {
        Definition def = FlowEngine.newDef()
            .setFlowCode(flowCode)
            .setFlowName(flowCode)
            .setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        return def;
    }

    private void node(Definition def, String code, Integer nodeType, String permission
        , String eventType, String eventName, String attachedTo, String nodeConfig) {
        FlowEngine.nodeService().save(FlowEngine.newNode()
            .setDefinitionId(def.getId())
            .setNodeCode(code)
            .setNodeName(code)
            .setNodeType(nodeType)
            .setNodeRatio("0")
            .setPermissionFlag(permission)
            .setEventType(eventType)
            .setEventName(eventName)
            .setAttachedToNodeCode(attachedTo)
            .setNodeConfig(nodeConfig));
    }

    private void skip(Definition def, String now, Integer nowType, String next, Integer nextType) {
        FlowEngine.skipService().save(FlowEngine.newSkip()
            .setDefinitionId(def.getId())
            .setNowNodeCode(now)
            .setNowNodeType(nowType)
            .setNextNodeCode(next)
            .setNextNodeType(nextType)
            .setSkipName(now + "_" + next)
            .setSkipType(SkipType.PASS.getKey()));
    }
}
