package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.strategy.VoteSignStrategy;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 票签完成条件规则特征测试：驳回计数（rejectCount=N，含通过票提前达成分支）
 * 与默认表达式（default@@...，表达式可见票签统计变量）。规则评估顺序为
 * Expression → PassCount → RejectCount → PassRatio。
 *
 * @author warm
 */
class VoteRuleCharacteristicTest {

    static {
        // 注册 default@@ 前缀的票签策略桩：解析 "passNum>=N"，按表达式统计变量判定
        ExpressionUtil.setExpression(new VoteSignStrategy() {
            @Override
            public String getType() {
                return FlowCons.DEFAULT;
            }

            @Override
            public Boolean eval(String expression, Map<String, Object> variable) {
                String[] parts = expression.split(">=");
                int threshold = Integer.parseInt(parts[1].trim());
                return ((Number) variable.get("passNum")).intValue() >= threshold;
            }
        });
    }

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    /** 发起三人票签流程并推进到 apply 节点，返回该待办 */
    private Task atApply(String flowCode, String ratio, String businessId) {
        Instance instance = TestFlows.startCooperateFlow(flowCode, ratio
                , TestFlows.HANDLER + "@@lisi@@wangwu", businessId);
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        return TestFlows.currentTask(instance.getId());
    }

    @Test
    void rejectCount_secondRejectFlows() {
        Task task = atApply("vr1", "rejectCount=2", "biz-vr1");
        Instance instance = harness.insDao.raw(task.getInstanceId());

        // 第一票驳回：已驳回 0+1 < 2，暂存
        TestFlows.reject(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()), "未达驳回人数应暂存");

        // 第二票驳回：1+1 >= 2，按驳回流转回 draft
        TestFlows.reject(task.getId(), "lisi");
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("draft", pending.get(0).getNodeCode());
        assertEquals(FlowStatus.REJECT.getKey()
                , harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void rejectCount_passSideMajorityFlows() {
        Task task = atApply("vr2", "rejectCount=2", "biz-vr2");
        Instance instance = harness.insDao.raw(task.getInstanceId());

        // 通过票在 已通过+1 > 总数-驳回阈值(3-2=1) 时流转：第一票 1>1 不成立暂存
        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()), "通过票未过多数应暂存");

        // 第二票通过：2 > 1 成立，流转至 end
        TestFlows.pass(task.getId(), "lisi");
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey()
                , harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void expressionRule_evaluatesWithVoteStatistics() {
        Task task = atApply("vr3", "default@@passNum>=2", "biz-vr3");
        Instance instance = harness.insDao.raw(task.getInstanceId());

        // 表达式可见 passNum 为「本次投票前」已归档的通过票数：
        // 首票 passNum=0、次票 passNum=1，均 < 2 暂存
        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()), "passNum=0 未达阈值应暂存");
        TestFlows.pass(task.getId(), "lisi");
        assertNotNull(harness.taskDao.raw(task.getId()), "passNum=1 未达阈值应暂存");

        // 第三票 passNum=2 达阈值，流转至 end
        TestFlows.pass(task.getId(), "wangwu");
        assertEquals(0, harness.taskDao.size());
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey()
                , harness.insDao.raw(instance.getId()).getFlowStatus());
    }
}
