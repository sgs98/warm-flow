package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
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

/**
 * 并行网关退回特征测试：复刻「互斥分支（小于三天）→ 并行分叉（并行一→并行二 ∥ 并行同级）」拓扑，
 * 锁定三种退回语义——同分支上游回退兄弟分支不受影响；跨分叉退回触发一票否决、兄弟待办被删
 * 且重分叉后全部分支重建；汇聚点始终等待全部分支。
 *
 * @author warm
 */
class ParallelRejectCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        parallelRejectFlow("prj");
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    /**
     * start → sq申请人 → x1互斥(gt:大于三天→第四个 / lt:小于三天) → x2互斥汇聚
     * → forkP并行 → [pa1并行一 → pa2并行二] ∥ [pb1并行同级] → joinP并行汇聚 → end
     */
    private void parallelRejectFlow(String flowCode) {
        Definition def = FlowEngine.newDef()
                .setFlowCode(flowCode)
                .setFlowName("并行退回特征流程")
                .setVersion("1")
                .setIsPublish(PublishStatus.PUBLISHED.getKey())
                .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(def);
        node(def, "start", NodeType.START.getKey());
        node(def, "sq", NodeType.BETWEEN.getKey());
        node(def, "x1", NodeType.SERIAL.getKey());
        node(def, "gt", NodeType.BETWEEN.getKey());
        node(def, "f4", NodeType.BETWEEN.getKey());
        node(def, "lt", NodeType.BETWEEN.getKey());
        node(def, "x2", NodeType.SERIAL.getKey());
        node(def, "forkP", NodeType.PARALLEL.getKey());
        node(def, "pa1", NodeType.BETWEEN.getKey());
        node(def, "pa2", NodeType.BETWEEN.getKey());
        node(def, "pb1", NodeType.BETWEEN.getKey());
        node(def, "joinP", NodeType.PARALLEL.getKey());
        node(def, "end", NodeType.END.getKey());
        skip(def, "start", "sq");
        skip(def, "sq", "x1");
        skip(def, "x1", "gt", "eq@@route|gt");
        skip(def, "x1", "lt", "eq@@route|lt");
        skip(def, "gt", "f4");
        skip(def, "f4", "x2");
        skip(def, "lt", "x2");
        skip(def, "x2", "forkP");
        skip(def, "forkP", "pa1");
        skip(def, "forkP", "pb1");
        skip(def, "pa1", "pa2");
        skip(def, "pa2", "joinP");
        skip(def, "pb1", "joinP");
        skip(def, "joinP", "end");
    }

    private void node(Definition def, String code, Integer type) {
        FlowEngine.nodeService().save(FlowEngine.newNode()
                .setDefinitionId(def.getId())
                .setNodeCode(code)
                .setNodeName(code)
                .setNodeType(type)
                .setNodeRatio("0")
                .setPermissionFlag(TestFlows.HANDLER));
    }

    private void skip(Definition def, String now, String next) {
        skip(def, now, next, null);
    }

    private void skip(Definition def, String now, String next, String condition) {
        org.dromara.warm.flow.core.entity.Skip skip = FlowEngine.newSkip()
                .setDefinitionId(def.getId())
                .setNowNodeCode(now)
                .setNextNodeCode(next)
                .setSkipName(now + "_" + next)
                .setSkipType(SkipType.PASS.getKey());
        if (condition != null) {
            skip.setSkipCondition(condition);
        }
        FlowEngine.skipService().save(skip);
    }

    /**
     * 走「小于三天」分支并推进到并行分叉之后：待办为并行一 + 并行同级。
     */
    private Instance startToParallelFork(String biz) {
        WorkflowContext ctx = TestFlows.context(TestFlows.HANDLER);
        ctx.setVariables(new HashMap<>(Map.of("route", "lt")));
        Instance ins = FlowEngine.insService().start(biz, "prj", ctx);
        TestFlows.pass(taskAt(ins, "sq").getId(), TestFlows.HANDLER);
        TestFlows.pass(taskAt(ins, "lt").getId(), TestFlows.HANDLER);
        assertEquals(List.of("pa1", "pb1"), pending(ins));
        return ins;
    }

    private Task taskAt(Instance ins, String nodeCode) {
        return TestFlows.pendingTasks(ins.getId()).stream()
                .filter(t -> nodeCode.equals(t.getNodeCode())).findFirst()
                .orElseThrow(() -> new IllegalStateException("无待办@" + nodeCode + " 实际=" + pending(ins)));
    }

    private List<String> pending(Instance ins) {
        return TestFlows.pendingTasks(ins.getId()).stream().map(Task::getNodeCode).sorted().toList();
    }

    private void rejectTo(Instance ins, String fromNode, String targetNodeCode) {
        WorkflowContext ctx = TestFlows.context(TestFlows.HANDLER);
        ctx.setTargetNodeCode(targetNodeCode);
        FlowEngine.taskService().execute(taskAt(ins, fromNode).getId(), ctx, SkipType.REJECT.getKey());
    }

    private String instanceStatus(Instance ins) {
        return harness.insDao.raw(ins.getId()).getFlowStatus();
    }

    @Test
    void parallelTwo_rejectToBranchOutside_vetoesSiblingAndReforksAllBranches() {
        Instance ins = startToParallelFork("biz-s1");
        TestFlows.pass(taskAt(ins, "pa1").getId(), TestFlows.HANDLER);

        // 并行二退回分支外的「小于三天」：只剩目标待办，实例回退
        rejectTo(ins, "pa2", "lt");
        assertEquals(List.of("lt"), pending(ins));
        assertEquals(FlowStatus.BACK.getKey(), instanceStatus(ins));
        // 一票否决的兄弟待办「并行同级」被删除，并按「失效」归档留痕（节点、办理人可审计）
        assertEquals(1, harness.hisTaskDao.all().stream()
                .filter(h -> "pb1".equals(h.getNodeCode()) && TaskStatus.INVALID.getKey().equals(h.getFlowStatus())
                        && "退回一票否决，待办自动失效".equals(h.getMessage()))
                .count()
                , "被否决的兄弟待办应留一条失效历史（含否决文案）: " + harness.hisTaskDao.all());

        // 重过小于三天：并行分叉整体重做，两条分支（含已完成的并行一）全部重建
        TestFlows.pass(taskAt(ins, "lt").getId(), TestFlows.HANDLER);
        assertEquals(List.of("pa1", "pb1"), pending(ins));
        assertEquals(FlowStatus.WAITING.getKey(), instanceStatus(ins));

        // 重走两条分支后正常结束
        TestFlows.pass(taskAt(ins, "pa1").getId(), TestFlows.HANDLER);
        TestFlows.pass(taskAt(ins, "pa2").getId(), TestFlows.HANDLER);
        assertEquals(List.of("pb1"), pending(ins), "汇聚点应等待另一分支");
        TestFlows.pass(taskAt(ins, "pb1").getId(), TestFlows.HANDLER);
        assertEquals(List.of(), pending(ins));
        assertEquals(FlowStatus.FINISHED.getKey(), instanceStatus(ins));
    }

    @Test
    void parallelOne_rejectToBranchOutside_vetoesSiblingAndReforksAllBranches() {
        Instance ins = startToParallelFork("biz-s2");

        // 并行一（分支首个节点）退回小于三天：与并行二退回同理，兄弟并行同级被否决删除
        rejectTo(ins, "pa1", "lt");
        assertEquals(List.of("lt"), pending(ins));
        assertEquals(FlowStatus.BACK.getKey(), instanceStatus(ins));

        TestFlows.pass(taskAt(ins, "lt").getId(), TestFlows.HANDLER);
        assertEquals(List.of("pa1", "pb1"), pending(ins), "重过目标节点后两条并行分支全部重建");
    }

    @Test
    void parallelTwo_rejectToSameBranchUpstream_siblingKeepsPending() {
        Instance ins = startToParallelFork("biz-s3");
        TestFlows.pass(taskAt(ins, "pa1").getId(), TestFlows.HANDLER);

        // 并行二退回同分支上游的并行一：仅本分支回退，兄弟分支并行同级保持待办
        rejectTo(ins, "pa2", "pa1");
        assertEquals(List.of("pa1", "pb1"), pending(ins));
        assertEquals(FlowStatus.BACK.getKey(), instanceStatus(ins));

        // 本分支重走后，汇聚点仍等待未完成的兄弟分支，全部办结才结束
        TestFlows.pass(taskAt(ins, "pa1").getId(), TestFlows.HANDLER);
        assertEquals(List.of("pa2", "pb1"), pending(ins));
        TestFlows.pass(taskAt(ins, "pa2").getId(), TestFlows.HANDLER);
        assertEquals(List.of("pb1"), pending(ins));
        TestFlows.pass(taskAt(ins, "pb1").getId(), TestFlows.HANDLER);
        assertEquals(List.of(), pending(ins));
        assertEquals(FlowStatus.FINISHED.getKey(), instanceStatus(ins));
    }
}
