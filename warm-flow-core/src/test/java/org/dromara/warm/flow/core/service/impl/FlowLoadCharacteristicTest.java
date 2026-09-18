package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.FlowDto;
import org.dromara.warm.flow.core.entity.Form;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 表单读取路径特征测试：load/hisLoad 为只读路径——无自定义表单时不产生
 * 办理人/表单副作用查询（无副作用锁，改造前后都必须保持），
 * 节点自定义表单时返回表单对象。
 *
 * @author warm
 */
class FlowLoadCharacteristicTest {

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
    void load_withoutForm_hasNoSideQueries() {
        Instance instance = TestFlows.start("fl1", "biz-f1");
        Task task = TestFlows.currentTask(instance.getId());
        harness.reset();

        FlowDto dto = FlowEngine.taskService().load(task.getId());

        assertNull(dto.getForm(), "无自定义表单应返回空表单");
        assertNull(dto.getData(), "未设置 formData 变量应返回空数据");
        // 只读路径：不查办理人、不查表单
        assertTrue(harness.daoLog.stream().noneMatch(l -> l.startsWith("UserMemDao.")),
                "load 不应产生办理人查询: " + harness.daoLog);
        assertTrue(harness.daoLog.stream().noneMatch(l -> l.startsWith("FormMemDao.")),
                "load 不应产生表单查询: " + harness.daoLog);
        // 任务主键仅查一次
        assertEquals(1, harness.daoLog.stream().filter(l -> l.startsWith("TaskMemDao.selectById[")).count());
    }

    @Test
    void load_withNodeCustomForm_returnsForm() {
        // 建流后、发起前配置节点自定义表单：addTask 在发起时把 formCustom/formPath 拷贝到待办
        TestFlows.serialFlow("fl2");
        Form form = FlowEngine.newForm().setFormCode("f1").setFormName("测试表单");
        harness.formDao.save(form);
        Node applyNode = harness.nodeDao.all().stream()
                .filter(n -> "apply".equals(n.getNodeCode())).findFirst().orElseThrow();
        harness.nodeDao.raw(applyNode.getId())
                .setFormCustom("Y").setFormPath(String.valueOf(form.getId()));
        Instance instance = FlowEngine.insService().start("biz-f2", "fl2", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());
        harness.reset();

        FlowDto dto = FlowEngine.taskService().load(task.getId());

        assertNotNull(dto.getForm(), "节点自定义表单应返回表单对象");
        assertEquals(form.getId(), dto.getForm().getId());
        assertEquals(1, harness.daoLog.stream()
                .filter(l -> l.startsWith("FormMemDao.selectById[")).count());
    }

    @Test
    void hisLoad_returnsHistoricalDataWithoutForm() {
        Instance instance = TestFlows.start("fl3", "biz-f3");
        Task task = TestFlows.currentTask(instance.getId());
        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        Long applyHisId = harness.hisTaskDao.all().stream()
                .filter(h -> "apply".equals(h.getNodeCode())).findFirst().orElseThrow().getId();
        harness.reset();

        FlowDto dto = FlowEngine.taskService().hisLoad(applyHisId);

        assertNull(dto.getForm());
        assertNull(dto.getData());
        assertTrue(harness.daoLog.stream().noneMatch(l -> l.startsWith("UserMemDao.")),
                "hisLoad 不应产生办理人查询: " + harness.daoLog);
    }
}
