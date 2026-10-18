package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ChartStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;
import org.dromara.warm.flow.core.strategy.ServiceTaskStrategy;
import org.dromara.warm.flow.core.service.task.ServiceTaskHandler;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingGlobalListener;
import org.dromara.warm.flow.core.test.listener.RecordingServiceTaskHandler;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTaskCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        ServiceTaskStrategy.EXPRESSION_STRATEGY_LIST.clear();
        RecordingServiceTaskHandler.LAST_VARIABLES = null;
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void startExecutesServiceThenCreatesHumanTaskNoUser() {
        TestFlows.serviceThenHumanFlow("svc1",
            "{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
                + RecordingServiceTaskHandler.class.getName() + "\"}}");
        Instance instance = FlowEngine.insService().start("biz-s1", "svc1", TestFlows.context(TestFlows.HANDLER));

        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(Set.of("apply"), pending.stream().map(Task::getNodeCode).collect(Collectors.toSet()));
        assertEquals(1, harness.userDao.size(), "只有人工节点产生办理人，服务任务不产生 flow_user");
        assertEquals("ran", RecordingServiceTaskHandler.LAST_VARIABLES.get("svcFlag"));

        HisTask serviceHis = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(h -> "s1".equals(h.getNodeCode())).findFirst().orElseThrow();
        assertEquals(TaskStatus.PASS.getKey(), serviceHis.getFlowStatus());
        assertEquals(SkipType.NONE.getKey(), serviceHis.getSkipType());
        assertEquals(6, serviceHis.getNodeType());
        assertNotNull(serviceHis.getNodeConfig());
        assertEquals("apply", instance.getNodeCode());
    }

    @Test
    void parallelJoinWaitsServiceBranchThenHuman() {
        TestFlows.parallelWithServiceFlow("svcPar", classConfig());
        Instance instance = FlowEngine.insService().start("biz-par", "svcPar", TestFlows.context(TestFlows.HANDLER));

        // 服务分支先执行：汇聚等待人工分支，只剩 a1 待办，实例未提前推进到 end
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(Set.of("a1"), pending.stream().map(Task::getNodeCode).collect(Collectors.toSet()));
        assertNotEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());

        // a1 完成后汇聚放行，实例到达 end 并完成
        TestFlows.pass(pending.get(0).getId(), TestFlows.HANDLER);
        Instance finished = harness.insDao.raw(instance.getId());
        assertEquals("end", finished.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), finished.getFlowStatus());
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
    }

    @Test
    void serviceNodeFiresStartFinishAndSuccessorCreate() {
        TestFlows.serviceThenHumanFlow("svcEvent", classConfig());
        FlowEngine.insService().start("biz-event", "svcEvent", TestFlows.context(TestFlows.HANDLER));

        List<String> events = RecordingGlobalListener.EVENTS;
        assertTrue(events.stream().anyMatch(event -> event.startsWith("g:start{node=s1,")),
            "服务任务应在处理器执行前触发 start 监听器");
        assertTrue(events.stream().anyMatch(event -> event.startsWith("g:finish{node=s1,")),
            "服务任务应在后继解析完成后触发 finish 监听器");
        assertTrue(events.stream().anyMatch(event -> event.startsWith("g:create{node=apply,")),
            "自动推进创建的后继人工节点应触发 create 监听器");
    }

    @Test
    void successorHumanTaskAssignmentRewritesHandlersBeforePersist() {
        TestFlows.serviceThenHumanFlow("svcAssign", classConfig());
        List<String> assignedNodes = new ArrayList<>();
        harness.register(GlobalListener.class, new GlobalListener() {
            @Override
            public void assignment(ListenerVariable variable) {
                if (CollUtil.isEmpty(variable.getNextTasks())) {
                    return;
                }
                for (Task task : variable.getNextTasks()) {
                    assignedNodes.add(task.getNodeCode());
                    if (NodeType.isService(task.getNodeType())) {
                        continue;
                    }
                    task.setPermissionList(StreamUtils.toList(task.getPermissionList()
                        , flag -> flag + "-resolved"));
                }
            }
        });
        FlowEngine.initGlobalListener(null);
        FlowEngine.insService().start("biz-assign", "svcAssign", TestFlows.context(TestFlows.HANDLER));

        assertTrue(assignedNodes.contains("apply"),
            "自动推进创建的后继人工节点应触发 assignment 监听器");
        assertEquals(List.of(TestFlows.HANDLER + "-resolved")
            , StreamUtils.toList(harness.userDao.all(), User::getProcessedBy)
            , "分派监听器对权限标识的改写须在办理人落库前生效");
    }

    @Test
    void serviceLoopExceedsLimitFails() {
        TestFlows.serviceLoopFlow("svcLoop", classConfig());
        FlowException ex = assertThrows(FlowException.class, () -> FlowEngine.insService()
            .start("biz-loop", "svcLoop", TestFlows.context(TestFlows.HANDLER)));
        assertTrue(ex.getMessage().contains(ExceptionCons.SERVICE_TASK_LOOP_LIMIT));
    }

    @Test
    void autoAdvanceMergesChartMetadataForEachHop() {
        TestFlows.chainedServiceThenHumanFlow("svcChart", classConfig());
        Instance instance = FlowEngine.insService()
            .start("biz-chart", "svcChart", TestFlows.context(TestFlows.HANDLER));

        Map<String, Integer> chartStatus = chartStatus(instance.getId());
        assertEquals(ChartStatus.DONE.getKey(), chartStatus.get("start"), "已流转的开始节点已办理");
        assertEquals(ChartStatus.DONE.getKey(), chartStatus.get("s1"), "自动推进的服务任务应标记已办理");
        assertEquals(ChartStatus.DONE.getKey(), chartStatus.get("s2"), "连续服务任务应逐跳合并流程图元数据");
        assertEquals(ChartStatus.TO_DO.getKey(), chartStatus.get("apply"), "后继人工节点应标记待办理");
        assertEquals(ChartStatus.NOT_DONE.getKey(), chartStatus.get("end"), "未到达的节点应保持未办理");
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

    private static String classConfig() {
        return "{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
            + RecordingServiceTaskHandler.class.getName() + "\"}}";
    }

    @Test
    void expressionModeWritesResultVariableAndKeepsExecutionAccessible() {
        ServiceTaskStrategy.EXPRESSION_STRATEGY_LIST.add(new ServiceTaskStrategy() {
            @Override
            public String getType() {
                return "#";
            }

            @Override
            public Object eval(String expression, Map<String, Object> variable) {
                assertNotNull(variable.get("execution"), "#execution 访问入口应存在");
                return "NO-123";
            }
        });
        TestFlows.serviceThenHumanFlow("svcExpr",
            "{\"serviceTask\":{\"type\":\"expression\",\"expression\":\"#{@x.y()}\",\"resultVariable\":\"orderNo\"}}");
        Instance instance = FlowEngine.insService().start("biz-expr", "svcExpr", TestFlows.context(TestFlows.HANDLER));

        Map<String, Object> persisted = harness.insDao.raw(instance.getId()).getVariableMap();
        assertEquals("NO-123", persisted.get("orderNo"));
        assertFalse(persisted.containsKey("execution"), "上下文对象不应写入持久化流程变量");
        assertEquals("apply", TestFlows.currentTask(instance.getId()).getNodeCode());
    }

    @Test
    void delegateExpressionResolvesHandlerFromExpression() {
        ServiceTaskStrategy.EXPRESSION_STRATEGY_LIST.add(new ServiceTaskStrategy() {
            @Override
            public String getType() {
                return "#";
            }

            @Override
            public Object eval(String expression, Map<String, Object> variable) {
                return (ServiceTaskHandler) ctx -> ctx.setVariable("svcFlag", "delegated");
            }
        });
        TestFlows.serviceThenHumanFlow("svcDeleg",
            "{\"serviceTask\":{\"type\":\"delegateExpression\",\"expression\":\"#{@x}\"}}");
        Instance instance = FlowEngine.insService().start("biz-deleg", "svcDeleg", TestFlows.context(TestFlows.HANDLER));

        assertEquals("delegated", harness.insDao.raw(instance.getId()).getVariableMap().get("svcFlag"));
        assertEquals("apply", TestFlows.currentTask(instance.getId()).getNodeCode());
    }
}
