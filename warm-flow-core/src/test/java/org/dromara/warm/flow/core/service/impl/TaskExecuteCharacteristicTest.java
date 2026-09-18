package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingListener;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * execute 办理路径特征测试：锁定校验级联顺序、监听器时序、持久化顺序与查询次数基线。
 * 改造前后这些断言必须保持绿色。
 *
 * @author warm
 */
class TaskExecuteCharacteristicTest {

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
    void taskMissing_throwsBeforeAnyOtherCheck() {
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(9999L, TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.NOT_FOUNT_TASK, ex.getMessage());
        assertTrue(harness.daoLog.stream().noneMatch(l -> l.startsWith("InstanceMemDao.")),
                "任务缺失时应先于实例查询失败");
    }

    @Test
    void instanceMissing_throwsBeforeDefinitionCheck() {
        Instance instance = TestFlows.start("cascade1", "biz-c1");
        harness.insDao.removeRaw(instance.getId());
        Task task = taskOf(instance);
        harness.daoLog.clear();

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId(), TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.NOT_FOUNT_INSTANCE, ex.getMessage());
        assertTrue(harness.daoLog.stream().noneMatch(l -> l.startsWith("DefinitionMemDao.select")),
                "实例缺失时应先于定义查询失败");
    }

    @Test
    void definitionMissing_throwsBeforeActivityCheck() {
        Instance instance = TestFlows.start("cascade2", "biz-c2");
        Task task = taskOf(instance);
        harness.defDao.removeRaw(instance.getDefinitionId());

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId(), TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.NOT_FOUNT_DEF, ex.getMessage());
    }

    @Test
    void suspendedInstance_throwsNotActivity() {
        Instance instance = TestFlows.start("cascade3", "biz-c3");
        Task task = taskOf(instance);
        instance.setActivityStatus(0);
        harness.insDao.raw(instance.getId()).setActivityStatus(0);

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId(), TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.NOT_ACTIVITY, ex.getMessage());
    }

    @Test
    void finishedInstance_throwsFlowFinish() {
        Instance instance = TestFlows.start("cascade4", "biz-c4");
        Task task = taskOf(instance);
        harness.insDao.raw(instance.getId()).setFlowStatus(FlowStatus.FINISHED.getKey());

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId(), TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.FLOW_FINISH, ex.getMessage());
    }

    @Test
    void endNodeTask_failsOnMissingSkip() {
        Instance instance = TestFlows.start("cascade5", "biz-c5");
        Task task = taskOf(instance);
        task.setNodeCode("end").setNodeType(2);
        harness.taskDao.raw(task.getId()).setNodeCode("end").setNodeType(2);

        // 实际行为：execute 对 end 节点任务未做前置拦截，流转解析时因无出线报 NULL_SKIP_TYPE
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId(), TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.NULL_SKIP_TYPE, ex.getMessage());
    }

    @Test
    void lostCurrentNode_throwsLostCurNode() {
        Instance instance = TestFlows.start("cascade6", "biz-c6");
        Task task = taskOf(instance);
        harness.taskDao.raw(task.getId()).setNodeCode("bogus");

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId(), TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.LOST_CUR_NODE, ex.getMessage());
    }

    @Test
    void withoutPermission_throwsNullRoleNode() {
        Instance instance = TestFlows.start("auth1", "biz-a1");
        Task task = taskOf(instance);
        WorkflowContext context = new WorkflowContext();
        context.setHandler("lisi");
        context.setPermissions(List.of("lisi"));

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().execute(task.getId(), context, SkipType.PASS.getKey()));
        assertEquals(ExceptionCons.NULL_ROLE_NODE, ex.getMessage());
    }

    @Test
    void happyPath_locksListenerSequenceAndFinalState() {
        Instance instance = TestFlows.start("happy1", "biz-h1");
        Task task = taskOf(instance);
        harness.reset();

        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        Map<String, Object> variables = new HashMap<>();
        variables.put("days", 3);
        context.setVariables(variables);
        FlowEngine.taskService().execute(task.getId(), context, SkipType.PASS.getKey());

        List<String> events = nodeEvents();
        // 监听器顺序：start → assignment → finish；下一节点为 end，不触发 create
        assertTrue(events.get(0).startsWith("start{node=apply,task=apply,ctx=true,taskUsers=true"),
                "start 监听器需在 setUserList 之后、可见 context，实际: " + events.get(0));
        int assignmentIdx = indexOfTag(events, "assignment{node=apply,task=apply");
        int finishIdx = indexOfTag(events, "finish{node=apply,task=apply");
        assertTrue(assignmentIdx > 0, "缺少 assignment 监听器: " + events);
        assertTrue(finishIdx > assignmentIdx, "finish 应晚于 assignment: " + events);
        assertTrue(events.stream().noneMatch(e -> e.startsWith("create{")), "end 节点不应触发 create: " + events);

        // 终态：无待办、两条 PASS 历史、实例结束
        assertEquals(0, harness.taskDao.size(), "办结后不应残留待办: " + harness.daoLog);
        assertEquals(2, harness.hisTaskDao.size(), "start+apply 各一条历史");
        assertTrue(harness.hisTaskDao.all().stream().allMatch(h -> SkipType.PASS.getKey().equals(h.getSkipType())));
        Instance persisted = harness.insDao.raw(instance.getId());
        assertEquals("end", persisted.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), persisted.getFlowStatus());
        assertTrue(persisted.getVariable() != null && persisted.getVariable().contains("\"days\":3"),
                "实例变量应包含办理变量，实际: " + persisted.getVariable());
        assertEquals(0, harness.userDao.size(), "办结后办理人应清理");
    }

    @Test
    void happyPath_locksPersistenceOrderAndQueryBaseline() {
        Instance instance = TestFlows.start("happy2", "biz-h2");
        Task task = taskOf(instance);
        harness.reset();

        FlowEngine.taskService().execute(task.getId(), TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey());

        // 实际持久化顺序：历史(单条 save) → 删当前任务 → 删办理人 → 更新实例；
        // 流向 end 时无新任务/新办理人，空列表不触发 saveBatch
        int hisSave = indexOfPrefix("HisTaskMemDao.save[");
        int taskDelete = indexOfPrefix("TaskMemDao.deleteByIds");
        int userDelete = indexOfPrefix("UserMemDao.deleteByTaskIds");
        int insUpdate = indexOfPrefix("InstanceMemDao.updateById");
        int handUndoneQuery = indexOfPrefix("TaskMemDao.selectList[InstanceId=");
        assertTrue(taskDelete > hisSave, "历史应先于任务删除持久化: " + harness.daoLog);
        assertTrue(userDelete > taskDelete, "删办理人应晚于删任务: " + harness.daoLog);
        assertTrue(insUpdate > userDelete, "更新实例应晚于删办理人: " + harness.daoLog);
        assertTrue(handUndoneQuery > insUpdate, "handUndoneTask 查询应晚于实例更新: " + harness.daoLog);
        assertTrue(harness.daoLog.stream().noneMatch(l -> l.startsWith("TaskMemDao.saveBatch")),
                "end 流向不应保存新待办: " + harness.daoLog);

        // 查询次数基线（R 上下文加载仅一次 selectById）
        assertEquals(1, countPrefix("TaskMemDao.selectById["), "execute 路径任务主键查询基线: " + harness.daoLog);
    }

    private Task taskOf(Instance instance) {
        return TestFlows.currentTask(instance.getId());
    }

    private static List<String> nodeEvents() {
        return RecordingListener.EVENTS.stream()
                .filter(e -> !e.startsWith("g:"))
                .collect(Collectors.toList());
    }

    private static int indexOfTag(List<String> events, String tag) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(tag)) {
                return i;
            }
        }
        return -1;
    }

    private int indexOfPrefix(String prefix) {
        for (int i = 0; i < harness.daoLog.size(); i++) {
            if (harness.daoLog.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private long countPrefix(String prefix) {
        return harness.daoLog.stream().filter(l -> l.startsWith(prefix)).count();
    }
}
