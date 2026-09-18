package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
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
 * 实例级删除特征测试：锁定 deleteByInsIds 的激活校验与任务清理、
 * insService.remove 的全级联删除（待办/办理人/历史/实例）。
 *
 * @author warm
 */
class InstanceCleanupCharacteristicTest {

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
    void deleteByInsIds_suspendedInstance_throwsNotActivity() {
        Instance instance = TestFlows.start("ic1", "biz-i1");
        FlowEngine.insService().unActive(instance.getId());

        FlowException ex = assertThrows(FlowException.class,
                () -> FlowEngine.taskService().deleteByInsIds(List.of(instance.getId())));
        assertEquals(ExceptionCons.NOT_ACTIVITY, ex.getMessage());
        assertEquals(1, harness.taskDao.size(), "失败路径不应删除待办");
    }

    @Test
    void deleteByInsIds_removesTasks() {
        Instance instance = TestFlows.start("ic2", "biz-i2");

        assertTrue(FlowEngine.taskService().deleteByInsIds(List.of(instance.getId())));
        assertEquals(0, harness.taskDao.size());
        // 仅删待办：实例与历史保留
        assertEquals(1, harness.insDao.size());
        assertEquals(1, harness.hisTaskDao.size());
    }

    @Test
    void insRemove_cascadesTasksUsersHisInstances() {
        Instance instance = TestFlows.start("ic3", "biz-i3");

        assertTrue(FlowEngine.insService().remove(List.of(instance.getId())));
        assertEquals(0, harness.taskDao.size());
        assertEquals(0, harness.userDao.size());
        assertEquals(0, harness.hisTaskDao.size());
        assertEquals(0, harness.insDao.size());
    }
}
