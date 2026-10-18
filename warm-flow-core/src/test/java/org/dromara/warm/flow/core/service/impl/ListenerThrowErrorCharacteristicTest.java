package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.timer.EventTaskHandler;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 监听器显式抛错特征测试：验证宿主在节点监听器里读取流程变量后调用
 * {@code TaskService.throwError} 能命中挂在该节点上的错误边界，中断当前任务并沿
 * 边界出口推进——即「不用手动按钮、由监听器按变量自动进边界」的引擎层行为。
 *
 * @author warm
 */
class ListenerThrowErrorCharacteristicTest {

    /**
     * 判定型监听器类路径（本测试按事件类型挂到 pay 节点）。
     */
    private static final String THROW_ERROR_LISTENER =
        "org.dromara.warm.flow.core.test.listener.ThrowErrorOnVariableListener";

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        harness.register(EventTaskHandler.class, new EventRuntimeHandler());
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    /**
     * pay 节点的 create 监听器读到 payAmount 超过限额 1000 时抛错：pay 任务被中断置失效，
     * 流程沿错误边界出口转到 rescue，而不是停在 pay 待办。
     */
    @Test
    void createListenerThrowErrorInterruptsAndRoutesToBoundary() {
        Definition def = boundaryWithListenerFlow("lisErrBd", "create");
        Instance instance = FlowEngine.insService().start("biz-lis-bd", def.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        Task apply = TestFlows.currentTask(instance.getId());

        passWithVariables(apply.getId(), Map.of("payAmount", "5000"));

        // pay 任务被自动中断，不产生待办；流程转到 rescue
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("rescue", pending.get(0).getNodeCode());
        assertEquals("rescue", harness.insDao.raw(instance.getId()).getNodeCode());

        HisTask payHis = hisOf(instance.getId(), "pay");
        assertEquals(TaskStatus.INVALID.getKey(), payHis.getFlowStatus());
        assertTrue(payHis.getMessage().contains("错误边界事件中断"));
    }

    /**
     * 金额不超过限额时不抛错：pay 正常产生待办，等人工办理。
     */
    @Test
    void createListenerWithinLimitKeepsNormalTodo() {
        Definition def = boundaryWithListenerFlow("lisErrBdOk", "create");
        Instance instance = FlowEngine.insService().start("biz-lis-ok", def.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        Task apply = TestFlows.currentTask(instance.getId());

        passWithVariables(apply.getId(), Map.of("payAmount", "800"));

        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("pay", pending.get(0).getNodeCode());
        assertEquals("pay", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    /**
     * 变量在启动时传入（demo 的发起方式）：首节点办理不带变量，靠实例累积变量判定，
     * pay 的 create 监听器仍能读到超限金额并进边界。
     */
    @Test
    void startVariableStillTriggersBoundaryOnPayCreate() {
        Definition def = boundaryWithListenerFlow("lisErrBdStart", "create");
        WorkflowContext startContext = TestFlows.context(TestFlows.HANDLER);
        startContext.setVariables(new HashMap<>(Map.of("payAmount", "5000")));
        Instance instance = FlowEngine.insService().start("biz-lis-start", def.getFlowCode(), startContext);
        Task apply = TestFlows.currentTask(instance.getId());

        TestFlows.pass(apply.getId(), TestFlows.HANDLER);

        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("rescue", pending.get(0).getNodeCode());
        assertEquals("rescue", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    /**
     * 已知限制：把监听器挂在 pay 的 start 事件上、并在其中抛错不安全——抛错会在内层
     * 完成边界路由并删除 pay 任务，随后外层办理链在 persistAndFinalize 找不到该任务而
     * 抛出 {@code FlowException: 未找到待办任务!}。因此判定型监听器应挂 create 事件
     * （见 {@link #createListenerThrowErrorInterruptsAndRoutesToBoundary()}），不要挂 start。
     */
    @Test
    void startEventListenerThrowErrorIsUnsafe() {
        Definition def = boundaryWithListenerFlow("lisErrBdEvtStart", "start");
        Instance instance = FlowEngine.insService().start("biz-lis-evt-start", def.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        Task apply = TestFlows.currentTask(instance.getId());
        passWithVariables(apply.getId(), Map.of("payAmount", "800"));

        Task pay = TestFlows.currentTask(instance.getId());
        FlowException ex = assertThrows(FlowException.class,
            () -> passWithVariables(pay.getId(), Map.of("payAmount", "5000")));
        assertTrue(ex.getMessage().contains("未找到待办任务"), ex.getMessage());
    }

    /**
     * 以携带流程变量的上下文通过指定任务。
     */
    private void passWithVariables(Long taskId, Map<String, Object> variables) {
        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        context.setVariables(new HashMap<>(variables));
        FlowEngine.taskService().execute(taskId, context, SkipType.PASS.getKey());
    }

    /**
     * 取实例指定节点的最新历史任务。
     */
    private HisTask hisOf(Long instanceId, String nodeCode) {
        return harness.hisTaskDao.all().stream()
            .filter(his -> instanceId.equals(his.getInstanceId()) && nodeCode.equals(his.getNodeCode()))
            .reduce((a, b) -> b)
            .orElseThrow(() -> new AssertionError("缺少历史任务 " + nodeCode));
    }

    /**
     * 流程：start → apply(handler) → pay(handler, 指定事件监听器 + 错误边界 eb) → end；
     * 边界 eb(错误码 sys_err) 附着在 pay 上，出口指向 rescue；rescue(handler) → end。
     */
    private Definition boundaryWithListenerFlow(String flowCode, String listenerEventType) {
        Definition def = FlowEngine.newDef()
            .setFlowCode(flowCode)
            .setFlowName(flowCode)
            .setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null);
        node(def, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER);
        node(def, "pay", NodeType.BETWEEN.getKey(), TestFlows.HANDLER);
        node(def, "rescue", NodeType.BETWEEN.getKey(), TestFlows.HANDLER);
        node(def, "end", NodeType.END.getKey(), null);
        boundaryNode(def, "eb", "sys_err", "pay");

        // pay 节点的事件监听器：读变量决定是否抛错进边界
        Node pay = FlowEngine.nodeService()
            .getByDefIdAndNodeCode(def.getId(), "pay");
        pay.setListenerType(listenerEventType).setListenerPath(THROW_ERROR_LISTENER);
        FlowEngine.nodeService().updateById(pay);

        skip(def, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(def, "apply", NodeType.BETWEEN.getKey(), "pay", NodeType.BETWEEN.getKey());
        skip(def, "pay", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        skip(def, "eb", NodeType.BETWEEN.getKey(), "rescue", NodeType.BETWEEN.getKey());
        skip(def, "rescue", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    /**
     * 保存人工节点。
     */
    private void node(Definition def, String code, Integer nodeType, String permission) {
        FlowEngine.nodeService().save(FlowEngine.newNode()
            .setDefinitionId(def.getId())
            .setNodeCode(code)
            .setNodeName(code)
            .setNodeType(nodeType)
            .setNodeRatio("0")
            .setPermissionFlag(permission));
    }

    /**
     * 保存附着在指定任务节点上的错误边界事件节点。
     */
    private void boundaryNode(Definition def, String code, String errorCode, String attachedTo) {
        FlowEngine.nodeService().save(FlowEngine.newNode()
            .setDefinitionId(def.getId())
            .setNodeCode(code)
            .setNodeName(code)
            .setNodeType(NodeType.BETWEEN.getKey())
            .setNodeRatio("0")
            .setEventType(EventType.ERROR_BOUNDARY.getValue())
            .setEventName(errorCode)
            .setAttachedToNodeCode(attachedTo));
    }

    /**
     * 保存 PASS 类型连线。
     */
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
