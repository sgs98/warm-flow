package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingListener;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 委派（depute）特征测试：锁定委派守卫、委派记录落库、受托人办理短路
 * （办案权回还委托人、不流转、监听器只到 start）、委托人续办流转。
 *
 * @author warm
 */
class DeputeCharacteristicTest {

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
    void deputeGuards_throwOnEmptyAndDuplicate() {
        Instance instance = TestFlows.start("dp1", "biz-d1");
        Task task = TestFlows.currentTask(instance.getId());

        FlowException empty = assertThrows(FlowException.class, () -> FlowEngine.taskService()
                .updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER), List.of(), List.of()
                        , CooperateType.DEPUTE.getKey()));
        assertEquals(ExceptionCons.NULL_DEPUTE_HANDLER, empty.getMessage());

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
                , List.of("lisi"), List.of(), CooperateType.DEPUTE.getKey());
        FlowException dup = assertThrows(FlowException.class, () -> FlowEngine.taskService()
                .updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER), List.of("lisi"), List.of()
                        , CooperateType.DEPUTE.getKey()));
        assertEquals(ExceptionCons.IS_ALREADY_DEPUTE, dup.getMessage());
    }

    @Test
    void deputeeHandles_returnsTaskToDeputorWithoutFlow() {
        Instance instance = TestFlows.start("dp2", "biz-d2");
        Task task = TestFlows.currentTask(instance.getId());
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
                , List.of("lisi"), List.of(), CooperateType.DEPUTE.getKey());
        int deputeHisBefore = harness.hisTaskDao.all().stream()
                .filter(h -> CooperateType.DEPUTE.getKey().equals(h.getCooperateType())).toList().size();
        harness.reset();

        // 受托人 lisi 办理：办案权回还 zhangsan，本次不流转
        TestFlows.pass(task.getId(), "lisi");

        assertNotNull(harness.taskDao.raw(task.getId()), "委派办理后任务应保留");
        List<org.dromara.warm.flow.core.entity.User> users = harness.userDao.all();
        assertEquals(1, users.size());
        assertEquals(TestFlows.HANDLER, users.get(0).getProcessedBy());
        assertEquals(UserType.APPROVAL.getKey(), users.get(0).getType(), "办案权应回还为 APPROVAL");

        // 受托办理历史：新增 1 条 DEPUTE（skipType=PASS、approver=受托人、collaborator=委托人）
        List<org.dromara.warm.flow.core.entity.HisTask> deputeHis = harness.hisTaskDao.all().stream()
                .filter(h -> CooperateType.DEPUTE.getKey().equals(h.getCooperateType())).toList();
        assertEquals(deputeHisBefore + 1, deputeHis.size());
        assertEquals(1, deputeHis.stream()
                .filter(h -> SkipType.PASS.getKey().equals(h.getSkipType())).count());
        assertTrue(deputeHis.stream()
                .filter(h -> SkipType.PASS.getKey().equals(h.getSkipType()))
                .allMatch(h -> "lisi".equals(h.getApprover()) && TestFlows.HANDLER.equals(h.getCollaborator()))
                , "受托办理历史 approver/collaborator 应为受托人/委托人");

        // 短路：只触发 start 监听器
        List<String> nodeEvents = RecordingListener.EVENTS.stream()
                .filter(e -> !e.startsWith("g:")).map(e -> e.substring(0, e.indexOf('{'))).toList();
        assertEquals(List.of("start"), nodeEvents, "委派办理应短路在 start 之后: " + nodeEvents);

        // 实例未推进
        assertEquals("apply", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    @Test
    void deputorThenCompletes_flowsToEnd() {
        Instance instance = TestFlows.start("dp3", "biz-d3");
        Task task = TestFlows.currentTask(instance.getId());
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
                , List.of("lisi"), List.of(), CooperateType.DEPUTE.getKey());
        TestFlows.pass(task.getId(), "lisi");

        // 委托人续办：正常流转到 end
        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        assertEquals(0, harness.taskDao.size());
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        // 历史：发起 + 委派协作记录 + 受托办理 + 流转归档 = 4（无签票记录）
        assertEquals(4, harness.hisTaskDao.size());
    }
}
