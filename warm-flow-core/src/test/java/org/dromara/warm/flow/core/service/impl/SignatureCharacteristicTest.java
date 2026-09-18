package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 加签/减签特征测试：锁定协作守卫（空对象、重复加签、最后一人不可减签）、
 * 权限门（NOT_AUTHORITY）与加减签的办理人/协作历史落库。
 *
 * @author warm
 */
class SignatureCharacteristicTest {

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
    void addSignature_guards_thenAddsApprover() {
        Instance instance = TestFlows.start("sg1", "biz-g1");
        Task task = TestFlows.currentTask(instance.getId());

        FlowException empty = assertThrows(FlowException.class, () -> FlowEngine.taskService()
                .updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER), List.of(), List.of()
                        , CooperateType.ADD_SIGNATURE.getKey()));
        assertEquals(ExceptionCons.NULL_ADD_SIGNATURE_HANDLER, empty.getMessage());

        FlowException dup = assertThrows(FlowException.class, () -> FlowEngine.taskService()
                .updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
                        , List.of(TestFlows.HANDLER), List.of(), CooperateType.ADD_SIGNATURE.getKey()));
        assertEquals(ExceptionCons.IS_ALREADY_SIGN, dup.getMessage());

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
                , List.of("wangwu"), List.of(), CooperateType.ADD_SIGNATURE.getKey());

        // 加签后：新办理人以 APPROVAL 持有任务，协作历史 ADD_SIGNATURE
        List<org.dromara.warm.flow.core.entity.User> users = harness.userDao.all();
        assertEquals(2, users.size());
        assertEquals(1, users.stream()
                .filter(u -> "wangwu".equals(u.getProcessedBy())
                        && UserType.APPROVAL.getKey().equals(u.getType())).count());
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> CooperateType.ADD_SIGNATURE.getKey().equals(h.getCooperateType())).count());
        assertNotNull(harness.taskDao.raw(task.getId()), "加签不应改变待办");
    }

    @Test
    void reductionSignature_lastUser_throws() {
        Instance instance = TestFlows.start("sg2", "biz-g2");
        Task task = TestFlows.currentTask(instance.getId());

        // 仅剩一名办理人时不可减签
        FlowException ex = assertThrows(FlowException.class, () -> FlowEngine.taskService()
                .updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER), List.of(), List.of("zhangsan")
                        , CooperateType.REDUCTION_SIGNATURE.getKey()));
        assertEquals(ExceptionCons.REDUCTION_SIGN_ONE_ERROR, ex.getMessage());
        assertEquals(1, harness.userDao.size(), "失败路径不应清理办理人");
    }

    @Test
    void reductionSignature_removesHandler_archivesHis() {
        Instance instance = TestFlows.start("sg3", "biz-g3");
        Task task = TestFlows.currentTask(instance.getId());
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
                , List.of("wangwu"), List.of(), CooperateType.ADD_SIGNATURE.getKey());

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
                , List.of(), List.of("wangwu"), CooperateType.REDUCTION_SIGNATURE.getKey());

        assertFalse(harness.userDao.all().stream()
                .anyMatch(u -> "wangwu".equals(u.getProcessedBy())), "被减签人应移除");
        assertEquals(1, harness.userDao.size());
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> CooperateType.REDUCTION_SIGNATURE.getKey().equals(h.getCooperateType())).count());
        assertNotNull(harness.taskDao.raw(task.getId()), "减签不应改变待办");
    }

    @Test
    void transferWithoutAuthority_throwsNotAuthority() {
        Instance instance = TestFlows.start("sg4", "biz-g4");
        Task task = TestFlows.currentTask(instance.getId());

        // 无忽略标记的普通上下文：办理人不在任务权限内 → NOT_AUTHORITY
        WorkflowContext context = new WorkflowContext();
        context.setHandler("mali");
        context.setPermissions(List.of("mali"));

        FlowException ex = assertThrows(FlowException.class, () -> FlowEngine.taskService()
                .updateHandlers(task.getId(), context, List.of("lisi"), List.of()
                        , CooperateType.TRANSFER.getKey()));
        assertEquals(ExceptionCons.NOT_AUTHORITY, ex.getMessage());
        assertEquals(1, harness.userDao.size(), "失败路径不应改动办理人");
        assertEquals(0, harness.hisTaskDao.all().stream()
                .filter(h -> Objects.equals(h.getTaskId(), task.getId())).count());
    }
}
