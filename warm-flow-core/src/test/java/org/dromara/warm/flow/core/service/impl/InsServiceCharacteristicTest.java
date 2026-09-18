package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实例级服务特征测试：实例激活/挂起（含重复操作守卫与挂起实例阻断办理）、
 * 变量删除持久化、按定义查询实例。
 *
 * @author warm
 */
class InsServiceCharacteristicTest {

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
    void activeUnActive_roundTripWithGuards() {
        Instance instance = TestFlows.start("ic1", "biz-ic1");

        assertTrue(FlowEngine.insService().unActive(instance.getId()));
        assertEquals(ActivityStatus.SUSPENDED.getKey()
                , harness.insDao.raw(instance.getId()).getActivityStatus());
        // 已挂起再挂起：报「已经挂起」（附录B错误常量已修复，原为复用「已经激活」文案）
        FlowException reSuspend = assertThrows(FlowException.class,
                () -> FlowEngine.insService().unActive(instance.getId()));
        assertEquals(ExceptionCons.INSTANCE_ALREADY_SUSPENDED, reSuspend.getMessage());

        assertTrue(FlowEngine.insService().active(instance.getId()));
        assertEquals(ActivityStatus.ACTIVITY.getKey()
                , harness.insDao.raw(instance.getId()).getActivityStatus());
        FlowException reActive = assertThrows(FlowException.class,
                () -> FlowEngine.insService().active(instance.getId()));
        assertEquals(ExceptionCons.INSTANCE_ALREADY_ACTIVITY, reActive.getMessage());

        FlowException unknown = assertThrows(FlowException.class,
                () -> FlowEngine.insService().active(-1L));
        assertEquals(ExceptionCons.NOT_FOUNT_INSTANCE, unknown.getMessage());
    }

    @Test
    void suspendedInstance_blocksTaskExecution() {
        Instance instance = TestFlows.start("ic2", "biz-ic2");
        Task task = TestFlows.currentTask(instance.getId());
        FlowEngine.insService().unActive(instance.getId());

        // 定义与实例任一挂起都阻断办理，校验发生在任务加载阶段
        FlowException ex = assertThrows(FlowException.class,
                () -> TestFlows.pass(task.getId(), TestFlows.HANDLER));
        assertEquals(ExceptionCons.NOT_ACTIVITY, ex.getMessage());
    }

    @Test
    void removeVariables_deletesKeysAndPersists() {
        TestFlows.serialFlow("ic3");
        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        Map<String, Object> variables = new HashMap<>();
        variables.put("days", 3);
        variables.put("level", "high");
        context.setVariables(variables);
        Instance instance = FlowEngine.insService().start("biz-ic3", "ic3", context);

        FlowEngine.insService().removeVariables(instance.getId(), "days");

        Map<String, Object> remaining = harness.insDao.raw(instance.getId()).getVariableMap();
        assertFalse(remaining.containsKey("days"), "目标变量应被删除");
        assertEquals("high", remaining.get("level"), "其余变量保留");
    }

    @Test
    void getByDefId_and_listByDefIds() {
        Definition def = TestFlows.serialFlow("ic4");
        FlowEngine.insService().start("biz-ic4a", "ic4", TestFlows.context(TestFlows.HANDLER));
        FlowEngine.insService().start("biz-ic4b", "ic4", TestFlows.context(TestFlows.HANDLER));

        List<Instance> byDefId = FlowEngine.insService().getByDefId(def.getId());
        List<Instance> byDefIds = FlowEngine.insService().listByDefIds(List.of(def.getId()));
        assertEquals(2, byDefId.size());
        assertEquals(2, byDefIds.size());
    }
}
