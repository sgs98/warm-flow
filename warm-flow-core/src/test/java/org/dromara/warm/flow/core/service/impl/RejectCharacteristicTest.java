package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingServiceTaskHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * reject 驳回路径特征测试：锁定驳回线解析前置校验、一票否决对退回目标后置待办的
 * 清理语义（先按「失效」归档留痕再删除）与驳回路径任务查询基线（oneVoteVeto ×1、
 * handUndoneTask 因实例未到 end 不触发）。
 *
 * @author warm
 */
class RejectCharacteristicTest {

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
    void rejectWithoutRejectLine_throwsNullSkipType() {
        Instance instance = TestFlows.start("rej0", "biz-r0");
        Task task = TestFlows.currentTask(instance.getId());

        // 串行流程只有 PASS 线，驳回找不到匹配连线
        FlowException ex = assertThrows(FlowException.class,
                () -> TestFlows.reject(task.getId(), TestFlows.HANDLER));
        assertEquals(ExceptionCons.NULL_SKIP_TYPE, ex.getMessage());
        assertEquals(1, harness.taskDao.size(), "失败路径不应清理待办");
    }

    @Test
    void oneVoteVeto_archivesSuffixSiblingTasksAsInvalid() {
        Instance instance = TestFlows.startRejectFlow("rej1", "biz-r1");
        Task applyTask = TestFlows.currentTask(instance.getId());
        TestFlows.pass(applyTask.getId(), TestFlows.HANDLER);
        Task auditTask = TestFlows.currentTask(instance.getId());
        // 种入 audit 节点的兄弟待办（模拟并行分支遗留），位于驳回目标 apply 的后置节点上
        Task sibling = FlowEngine.newTask()
                .setDefinitionId(auditTask.getDefinitionId())
                .setInstanceId(instance.getId())
                .setNodeCode("audit")
                .setNodeName("audit")
                .setNodeType(auditTask.getNodeType())
                .setFlowStatus(FlowStatus.WAITING.getKey());
        FlowEngine.taskService().save(sibling);
        FlowEngine.userService().save(FlowEngine.userService().structureUser(sibling.getId()
                , "wangwu", UserType.APPROVAL.getKey(), TestFlows.HANDLER));
        harness.reset();

        TestFlows.reject(auditTask.getId(), TestFlows.HANDLER);

        // 驳回目标待办：apply 节点、REJECT 状态
        List<Task> pending = FlowEngine.taskService().list(
                FlowEngine.newTask().setInstanceId(instance.getId()));
        assertEquals(1, pending.size(), "驳回后应仅有驳回目标待办: " + pending);
        assertEquals("apply", pending.get(0).getNodeCode());
        assertEquals(TaskStatus.BACK.getKey(), pending.get(0).getFlowStatus());
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals("apply", persisted.getNodeCode());
        assertEquals(FlowStatus.BACK.getKey(), persisted.getFlowStatus());

        // 一票否决：后置兄弟待办先按「失效」归档留痕，再删除待办与办理人
        assertNull(harness.taskDao.raw(sibling.getId()), "后置兄弟待办应被一票否决删除");
        assertTrue(harness.userDao.all().stream()
                        .noneMatch(u -> Objects.equals(u.getAssociated(), sibling.getId())),
                "兄弟待办办理人应一并清理");
        assertTrue(harness.hisTaskDao.all().stream()
                        .anyMatch(h -> Objects.equals(h.getTaskId(), sibling.getId())
                                && TaskStatus.INVALID.getKey().equals(h.getFlowStatus())
                                && "wangwu".equals(h.getApprover())
                                && "退回一票否决，待办自动失效".equals(h.getMessage())),
                "兄弟待办应留一条失效历史（含办理人与否决文案）: " + harness.hisTaskDao.all());
        // 驳回动作本身：当前任务转 REJECT 历史
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> Objects.equals(h.getTaskId(), auditTask.getId()))
                .filter(h -> SkipType.REJECT.getKey().equals(h.getSkipType())).count());
    }

    @Test
    void oneVoteVeto_preservesNewTargetAndInvalidatesOldLoopTasks() {
        Definition definition = TestFlows.rejectFlow("rejLoop");
        Skip loop = FlowEngine.skipService().getByDefId(definition.getId()).stream()
                .filter(skip -> "audit".equals(skip.getNowNodeCode()) && SkipType.isPass(skip.getSkipType()))
                .findFirst().orElseThrow();
        FlowEngine.skipService().updateById(loop.setNextNodeCode("apply"));
        Instance instance = FlowEngine.insService().start("biz-rej-loop", definition.getFlowCode()
                , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        Task auditTask = TestFlows.currentTask(instance.getId());
        List<Task> staleTasks = new ArrayList<>();
        for (String code : List.of("apply", "audit")) {
            Task stale = FlowEngine.newTask().setDefinitionId(definition.getId()).setInstanceId(instance.getId())
                    .setNodeCode(code).setNodeName(code).setNodeType(NodeType.BETWEEN.getKey())
                    .setFlowStatus(FlowStatus.WAITING.getKey());
            FlowEngine.taskService().save(stale);
            FlowEngine.userService().save(FlowEngine.userService().structureUser(stale.getId()
                    , "wangwu", UserType.APPROVAL.getKey(), TestFlows.HANDLER));
            staleTasks.add(stale);
        }

        TestFlows.reject(auditTask.getId(), TestFlows.HANDLER);

        Task target = TestFlows.currentTask(instance.getId());
        assertEquals("apply", target.getNodeCode());
        assertEquals(TaskStatus.BACK.getKey(), target.getFlowStatus());
        assertTrue(harness.userDao.all().stream().anyMatch(user -> Objects.equals(user.getAssociated(), target.getId())));
        for (Task stale : staleTasks) {
            assertNotEquals(stale.getId(), target.getId());
            assertNull(harness.taskDao.raw(stale.getId()));
            assertTrue(harness.userDao.all().stream().noneMatch(user -> Objects.equals(user.getAssociated(), stale.getId())));
            assertTrue(harness.hisTaskDao.all().stream().anyMatch(history -> Objects.equals(history.getTaskId(), stale.getId())
                    && TaskStatus.INVALID.getKey().equals(history.getFlowStatus()) && "wangwu".equals(history.getApprover())));
        }
        TestFlows.pass(target.getId(), TestFlows.HANDLER);
        assertEquals("audit", TestFlows.currentTask(instance.getId()).getNodeCode());
    }

    @Test
    void oneVoteVeto_preservesSuccessorsCreatedByServiceTasks() {
        String serviceConfig = "{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
                + RecordingServiceTaskHandler.class.getName() + "\"}}";
        Definition definition = TestFlows.chainedServiceThenHumanFlow("rejService", serviceConfig);
        FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(definition.getId())
                .setNowNodeCode("apply").setNowNodeType(NodeType.BETWEEN.getKey())
                .setNextNodeCode("s1").setNextNodeType(NodeType.SERVICE.getKey()).setSkipType(SkipType.REJECT.getKey()));
        Instance instance = FlowEngine.insService().start("biz-rej-service", definition.getFlowCode()
                , TestFlows.context(TestFlows.HANDLER));
        Task current = TestFlows.currentTask(instance.getId());
        Task stale = FlowEngine.newTask().setDefinitionId(definition.getId()).setInstanceId(instance.getId())
                .setNodeCode("apply").setNodeName("apply").setNodeType(NodeType.BETWEEN.getKey())
                .setFlowStatus(FlowStatus.WAITING.getKey());
        FlowEngine.taskService().save(stale);
        FlowEngine.userService().save(FlowEngine.userService().structureUser(stale.getId()
                , "wangwu", UserType.APPROVAL.getKey(), TestFlows.HANDLER));

        TestFlows.reject(current.getId(), TestFlows.HANDLER);

        Task target = TestFlows.currentTask(instance.getId());
        assertEquals("apply", target.getNodeCode());
        assertNotEquals(current.getId(), target.getId());
        assertNotEquals(stale.getId(), target.getId());
        assertNull(harness.taskDao.raw(stale.getId()));
        assertTrue(harness.userDao.all().stream().anyMatch(user -> Objects.equals(user.getAssociated(), target.getId())));
        assertTrue(harness.hisTaskDao.all().stream().anyMatch(history -> Objects.equals(history.getTaskId(), stale.getId())
                && TaskStatus.INVALID.getKey().equals(history.getFlowStatus())));
        assertEquals(4, harness.hisTaskDao.all().stream().filter(history -> NodeType.isService(history.getNodeType())).count());
        TestFlows.pass(target.getId(), TestFlows.HANDLER);
        assertEquals(0, TestFlows.pendingTasks(instance.getId()).size());
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void oneVoteVeto_clearsOldBranchesBeforeAutomaticNestedJoins() {
        Definition definition = FlowEngine.newDef().setFlowCode("rejNestedServices").setFlowName("嵌套服务节点驳回")
                .setVersion("1").setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(definition);
        String serviceConfig = "{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
                + RecordingServiceTaskHandler.class.getName() + "\"}}";
        Map<String, Integer> types = new LinkedHashMap<>();
        for (String code : List.of("start", "S0", "P", "X", "G", "R", "E", "S1", "S2", "J", "Y", "K", "D", "end")) {
            int type = switch (code) {
                case "start" -> NodeType.START.getKey();
                case "end" -> NodeType.END.getKey();
                case "S0", "S1", "S2" -> NodeType.SERVICE.getKey();
                case "P", "K" -> NodeType.PARALLEL.getKey();
                case "G", "J" -> NodeType.INCLUSIVE.getKey();
                case "R" -> NodeType.SERIAL.getKey();
                default -> NodeType.BETWEEN.getKey();
            };
            types.put(code, type);
            FlowEngine.nodeService().save(FlowEngine.newNode().setDefinitionId(definition.getId()).setNodeCode(code)
                    .setNodeName(code).setNodeType(type).setNodeRatio("0")
                    .setPermissionFlag(type == NodeType.BETWEEN.getKey() ? TestFlows.HANDLER : null)
                    .setNodeConfig(NodeType.isService(type) ? serviceConfig : null));
        }
        for (String[] link : List.of(new String[]{"start", "S0"}, new String[]{"S0", "P"}, new String[]{"P", "X"},
                new String[]{"P", "G"}, new String[]{"G", "R"}, new String[]{"G", "S2"}, new String[]{"R", "E"},
                new String[]{"R", "S1"}, new String[]{"E", "S1"}, new String[]{"S1", "J"}, new String[]{"S2", "J"},
                new String[]{"J", "Y"}, new String[]{"X", "K"}, new String[]{"Y", "K"}, new String[]{"K", "D"},
                new String[]{"D", "end"}, new String[]{"D", "S0"})) {
            FlowEngine.skipService().save(FlowEngine.newSkip().setDefinitionId(definition.getId())
                    .setNowNodeCode(link[0]).setNowNodeType(types.get(link[0])).setNextNodeCode(link[1])
                    .setNextNodeType(types.get(link[1])).setSkipType("D".equals(link[0]) && "S0".equals(link[1])
                            ? SkipType.REJECT.getKey() : SkipType.PASS.getKey())
                    .setSkipCondition("R".equals(link[0]) ? "eq@@repeat|" + "S1".equals(link[1]) : null));
        }
        var initial = TestFlows.context(TestFlows.HANDLER);
        initial.setVariables(new HashMap<>(Map.of("repeat", false)));
        Instance instance = FlowEngine.insService().start("biz-rej-nested-services", definition.getFlowCode(), initial);
        for (String code : List.of("E", "X", "Y")) {
            Task current = TestFlows.pendingTasks(instance.getId()).stream()
                    .filter(task -> code.equals(task.getNodeCode())).findFirst().orElseThrow();
            TestFlows.pass(current.getId(), TestFlows.HANDLER);
        }
        Task current = TestFlows.currentTask(instance.getId());
        assertEquals("D", current.getNodeCode());
        Task stale = FlowEngine.newTask().setDefinitionId(definition.getId()).setInstanceId(instance.getId())
                .setNodeCode("E").setNodeName("E").setNodeType(NodeType.BETWEEN.getKey())
                .setFlowStatus(FlowStatus.WAITING.getKey());
        FlowEngine.taskService().save(stale);
        FlowEngine.userService().save(FlowEngine.userService().structureUser(stale.getId()
                , "wangwu", UserType.APPROVAL.getKey(), TestFlows.HANDLER));
        var replay = TestFlows.context(TestFlows.HANDLER);
        replay.setVariables(new HashMap<>(Map.of("repeat", true)));

        FlowEngine.taskService().execute(current.getId(), replay, SkipType.REJECT.getKey());

        assertNull(harness.taskDao.raw(stale.getId()));
        assertEquals(List.of("X", "Y"), TestFlows.pendingTasks(instance.getId()).stream()
                .map(Task::getNodeCode).sorted().toList());
        assertFalse(TestFlows.pendingTasks(instance.getId()).stream().anyMatch(task -> NodeType.isService(task.getNodeType())));
        assertTrue(harness.hisTaskDao.all().stream().anyMatch(history -> stale.getId().equals(history.getTaskId())
                && TaskStatus.INVALID.getKey().equals(history.getFlowStatus())));
        Task first = TestFlows.pendingTasks(instance.getId()).stream()
                .filter(task -> "X".equals(task.getNodeCode())).findFirst().orElseThrow();
        TestFlows.pass(first.getId(), TestFlows.HANDLER);
        assertEquals("Y", TestFlows.currentTask(instance.getId()).getNodeCode());
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        Task next = TestFlows.currentTask(instance.getId());
        assertEquals("D", next.getNodeCode());
        assertNotEquals(current.getId(), next.getId());
    }

    @Test
    void locksTaskQueryBaseline_rejectPathQueriesOnce() {
        Instance instance = TestFlows.startRejectFlow("rej2", "biz-r2");
        Task applyTask = TestFlows.currentTask(instance.getId());
        TestFlows.pass(applyTask.getId(), TestFlows.HANDLER);
        Task auditTask = TestFlows.currentTask(instance.getId());
        Task sibling = FlowEngine.newTask()
                .setDefinitionId(auditTask.getDefinitionId())
                .setInstanceId(instance.getId())
                .setNodeCode("audit")
                .setNodeName("audit")
                .setNodeType(auditTask.getNodeType())
                .setFlowStatus(FlowStatus.WAITING.getKey());
        FlowEngine.taskService().save(sibling);
        harness.reset();

        TestFlows.reject(auditTask.getId(), TestFlows.HANDLER);

        // 驳回路径任务列表查询基线：仅 oneVoteVeto ×1；
        // handUndoneTask 因实例未流转到 end 节点不触发（R4 的两处同语句查询实际处于互斥分支）
        List<String> taskLists = harness.daoLog.stream()
                .filter(l -> l.startsWith("TaskMemDao.selectList")).toList();
        assertEquals(1, taskLists.size(), "驳回路径任务列表查询基线: " + harness.daoLog);
        // 驳回不走汇聚等待，不按节点编码查待办
        assertEquals(0, harness.daoLog.stream()
                .filter(l -> l.startsWith("TaskMemDao.getByInsIdAndNodeCodes")).count());
    }
}
