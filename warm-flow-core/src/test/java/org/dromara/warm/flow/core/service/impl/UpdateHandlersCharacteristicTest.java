package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * updateHandlers 协作路径特征测试：锁定转办防重、finish 监听器无 context（:411 语义锁）、
 * 协作历史记录与办理人全集派生视图的查询基线（R5 后：无类型条件查询）。
 *
 * @author warm
 */
class UpdateHandlersCharacteristicTest {

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
    void transferWithoutTarget_throwsNullTransferHandler() {
        Instance instance = TestFlows.start("uh1", "biz-u1");
        Task task = TestFlows.currentTask(instance.getId());

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER),
                        null, null, CooperateType.TRANSFER.getKey()));
        assertEquals(ExceptionCons.NULL_TRANSFER_HANDLER, ex.getMessage());
    }

    @Test
    void transfer_locksFinishListenerWithoutContextAndCooperateHis() {
        Instance instance = TestFlows.start("uh2", "biz-u2");
        Task task = TestFlows.currentTask(instance.getId());
        harness.daoLog.clear();

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER),
                List.of("lisi"), null, CooperateType.TRANSFER.getKey());

        // :411 语义锁：updateHandlers 的 finish 监听器不携带 context
        assertTrue(org.dromara.warm.flow.core.test.listener.RecordingListener.EVENTS.stream()
                        .anyMatch(e -> e.startsWith("finish{node=apply,task=apply,ctx=false")),
                "updateHandlers 的 finish 监听器应无 context: " + org.dromara.warm.flow.core.test.listener.RecordingListener.EVENTS);
        // 协作历史：转办记录一条 cooperateType=TRANSFER
        assertTrue(harness.hisTaskDao.all().stream()
                        .anyMatch(h -> CooperateType.TRANSFER.getKey().equals(h.getCooperateType())),
                "应写入转办协作历史: " + harness.hisTaskDao.all());
    }

    @Test
    void transferTwiceToSameTarget_throwsIsAlreadyTransfer() {
        Instance instance = TestFlows.start("uh3", "biz-u3");
        Task task = TestFlows.currentTask(instance.getId());
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER),
                List.of("lisi"), null, CooperateType.TRANSFER.getKey());

        // 实际防重语义：转办目标已存在转办记录时拦截（IS_ALREADY_TRANSFER）
        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER),
                        List.of("lisi"), null, CooperateType.TRANSFER.getKey()));
        assertEquals(ExceptionCons.IS_ALREADY_TRANSFER, ex.getMessage());
    }

    @Test
    void transfer_locksFullListUserQueryBaseline() {
        Instance instance = TestFlows.start("uh4", "biz-u4");
        Task task = TestFlows.currentTask(instance.getId());
        harness.daoLog.clear();

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER),
                List.of("lisi"), null, CooperateType.TRANSFER.getKey());

        // R5 后基线：守卫与权限门从无类型全集查询派生（改造前为按类型/按办理人条件查询）
        assertEquals(1, harness.daoLog.stream()
                        .filter(l -> l.startsWith("UserMemDao.selectList[")).count(),
                "updateHandlers 办理人全集查询基线（R5 后仅 1 次无类型查询）: " + harness.daoLog);
        assertTrue(harness.daoLog.stream()
                        .filter(l -> l.startsWith("UserMemDao."))
                        .noneMatch(l -> l.contains("Type=")),
                "不应再出现按类型条件查询: " + harness.daoLog);
        assertTrue(harness.daoLog.stream().noneMatch(l -> l.startsWith("UserMemDao.listByProcessedBys")),
                "不应再出现按办理人条件查询: " + harness.daoLog);
    }
}
