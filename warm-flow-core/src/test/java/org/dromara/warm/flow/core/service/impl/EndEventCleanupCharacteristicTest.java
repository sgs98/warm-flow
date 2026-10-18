package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.spi.DefaultStatusMapper;
import org.dromara.warm.flow.core.spi.StatusMapper;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结束收口特征测试：普通结束与终止结束都清理实例事件等待；结束事件在历史里留痕。
 *
 * @author may
 */
class EndEventCleanupCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void normalFinish_cancelsPendingWaits() {
        Instance instance = start(buildFlow("endClean", false));

        // 完成人工分支后实例收口，另一分支的消息捕获等待必须被取消，否则消息到达会推进已完成的实例
        Instance finished = complete(instance);
        assertEquals(NodeType.END.getKey(), finished.getNodeType());
        assertEquals(FlowStatus.FINISHED.getKey(), finished.getFlowStatus(),
            "普通结束的实例状态应为已完成");
        assertTrue(waits(instance.getId()).stream()
                .allMatch(w -> EventCons.STATUS_CANCELLED.equals(w.getStatus())),
            "普通结束也应取消本实例的等待记录: " + waits(instance.getId()));
    }

    @Test
    void terminateEnd_cancelsWaitsAndRecordsTrace() {
        Instance instance = start(buildFlow("endTerm", true));

        Instance terminated = complete(instance);
        assertEquals(NodeType.END.getKey(), terminated.getNodeType());
        // 结构上仍收口到 END 节点，但实例语义必须是「终止」而不是「已完成」，
        // 否则「严重异常终止」的单子和正常办结的单子在列表里无法区分
        assertEquals(FlowStatus.TERMINATE.getKey(), terminated.getFlowStatus(),
            "终止结束事件的实例状态应为终止");
        assertTrue(waits(instance.getId()).stream()
                .allMatch(w -> EventCons.STATUS_CANCELLED.equals(w.getStatus())),
            "终止结束应取消本实例的等待记录: " + waits(instance.getId()));

        // 结束事件留痕：历史里有一条 node_code=te 的记录，task_id 为哨兵 0
        List<HisTask> traces = FlowEngine.hisTaskService()
            .list(FlowEngine.newHisTask().setInstanceId(instance.getId())).stream()
            .filter(h -> "te".equals(h.getNodeCode())).toList();
        assertEquals(1, traces.size(), "结束事件应写入一条历史留痕");
        assertEquals(0L, traces.get(0).getTaskId());
        assertEquals(TaskStatus.FINISHED.getKey(), traces.get(0).getFlowStatus());
        assertEquals("流程由该终止结束事件终止", traces.get(0).getMessage());
    }

    @Test
    void normalFinish_manualTaskStatusKeepsInstanceFinished() {
        Instance instance = start(buildFlow("endManual", false));

        Instance finished = complete(instance, TaskStatus.PASS.getKey());
        assertEquals(FlowStatus.FINISHED.getKey(), finished.getFlowStatus(),
            "宿主手动指定的任务状态不应改写到实例状态上");
    }

    @Test
    void terminateEnd_withHostMapper_usesHostTerminateCode() {
        FlowEngine.setStatusMapper(new HostStatusMapper());
        try {
            Instance instance = start(buildFlow("endTermHost", true));

            Instance terminated = complete(instance);
            // 宿主自定义编码下实例「终止」是 termination，不能落回实例的「已完成」finish，
            // 也不能把任务的「结束」task_end 复制到实例上
            assertEquals("termination", terminated.getFlowStatus(),
                "终止结束应写入宿主的终止编码: " + terminated.getFlowStatus());
        } finally {
            FlowEngine.setStatusMapper(new DefaultStatusMapper());
        }
    }

    private List<EventWait> waits(Long instanceId) {
        return FlowEngine.eventWaitService().list(FlowEngine.newEventWait().setInstanceId(instanceId));
    }

    private Instance start(Definition def) {
        return FlowEngine.insService().start("BIZ-" + def.getFlowCode(), def.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
    }

    private Instance complete(Instance instance) {
        return complete(instance, null);
    }

    private Instance complete(Instance instance, String explicitStatus) {
        Task a1 = FlowEngine.taskService().list(FlowEngine.newTask().setInstanceId(instance.getId())).stream()
            .filter(t -> "a1".equals(t.getNodeCode())).findFirst().orElseThrow();
        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        if (explicitStatus != null) {
            context.setFlowStatus(explicitStatus);
        }
        return FlowEngine.taskService().execute(a1.getId(), context, SkipType.PASS.getKey());
    }

    /**
     * 构建 start → fork → [a1 → (te →) end, mc1 → end] 流程：一条人工分支、一条消息捕获分支。
     */
    private Definition buildFlow(String flowCode, boolean withTerminateEnd) {
        Definition def = FlowEngine.newDef()
            .setFlowCode(flowCode).setFlowName(flowCode).setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey(), null, null);
        node(def, "fork", NodeType.PARALLEL.getKey(), null, null);
        node(def, "a1", NodeType.BETWEEN.getKey(), "user:a", null);
        if (withTerminateEnd) {
            node(def, "te", NodeType.BETWEEN.getKey(), null, EventType.TERMINATE_END.getValue());
        }
        node(def, "mc1", NodeType.BETWEEN.getKey(), null, EventType.MESSAGE_CATCH.getValue())
            .setEventName("order.paid");
        node(def, "end", NodeType.END.getKey(), null, null);

        skip(def, "start", NodeType.START.getKey(), "fork", NodeType.PARALLEL.getKey());
        skip(def, "fork", NodeType.PARALLEL.getKey(), "a1", NodeType.BETWEEN.getKey());
        skip(def, "fork", NodeType.PARALLEL.getKey(), "mc1", NodeType.BETWEEN.getKey());
        if (withTerminateEnd) {
            skip(def, "a1", NodeType.BETWEEN.getKey(), "te", NodeType.BETWEEN.getKey());
            skip(def, "te", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        } else {
            skip(def, "a1", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        }
        skip(def, "mc1", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return def;
    }

    private Node node(Definition def, String code, Integer type, String permission, String eventType) {
        Node node = FlowEngine.newNode().setDefinitionId(def.getId()).setNodeCode(code)
            .setNodeName(code).setNodeType(type).setNodeRatio("0").setEventType(eventType);
        if (permission != null) {
            node.setPermissionFlag(permission);
        }
        FlowEngine.nodeService().save(node);
        return node;
    }

    private void skip(Definition def, String now, Integer nowType, String next, Integer nextType) {
        FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(def.getId())
            .setNowNodeCode(now).setNowNodeType(nowType).setNextNodeCode(next)
            .setNextNodeType(nextType).setSkipName(now + "_" + next)
            .setSkipType(SkipType.PASS.getKey()));
    }

    /**
     * 只覆盖三种编码，其余走 {@link StatusMapper} 默认实现：验证「终止」的传递不会被
     * 任务侧「结束」编码干扰。
     */
    private static class HostStatusMapper implements StatusMapper {

        @Override
        public String instanceStatus(FlowStatus semantic) {
            return switch (semantic) {
                case FINISHED -> "finish";
                case TERMINATE -> "termination";
                case APPROVAL -> "waiting";
                default -> StatusMapper.super.instanceStatus(semantic);
            };
        }

        @Override
        public String taskStatus(TaskStatus semantic) {
            return switch (semantic) {
                case FINISHED -> "task_end";
                case WAITING -> "to_do";
                default -> StatusMapper.super.taskStatus(semantic);
            };
        }
    }
}
