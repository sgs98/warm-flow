package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingServiceTaskHandler;
import org.dromara.warm.flow.core.utils.StreamUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定义级自动审批（{@code flow_definition.def_config} 中 {@code autoPass}）特征测试。
 *
 * <p>锁定行为：开关取自定义配置、只在办理时判定、只处理本次操作新建的待办、办理人集合包含当前
 * 办理人即以该办理人身份自动通过（会签 / 票签只代投一票）、历史写自动完成并可沿链连续推进。</p>
 *
 * @author warm
 */
class AutoPassCharacteristicTest {

    /**
     * 开启自动审批的流程定义配置。
     */
    private static final String AUTO_PASS_ON = "{\"autoPass\":true}";

    /**
     * 非当前办理人。
     */
    private static final String OTHER_HANDLER = "lisi";

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        RecordingServiceTaskHandler.LAST_VARIABLES = null;
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void sameHandlerSubsequentNodeAutoPassedInOneSubmit() {
        TestFlows.autoPassFlow("apSame", AUTO_PASS_ON, TestFlows.HANDLER, TestFlows.HANDLER);
        Instance instance = start("biz-ap-same", "apSame");
        Task apply = TestFlows.currentTask(instance.getId());
        assertEquals("apply", apply.getNodeCode());

        Instance finished = TestFlows.pass(apply.getId(), TestFlows.HANDLER);

        // 调用方拿到的是自动审批收尾后的实例位置与状态
        assertEquals("end", finished.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), finished.getFlowStatus());
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty(), "同一办理人的后继待办应被自动通过");
        Instance stored = harness.insDao.raw(instance.getId());
        assertEquals("end", stored.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), stored.getFlowStatus());

        // 自动通过复用标准办理链：历史写自动完成、无动作外的审批人仍为当前办理人
        HisTask auditHis = his(instance.getId(), "audit");
        assertEquals(FlowStatus.AUTO_PASS.getKey(), auditHis.getFlowStatus());
        assertEquals(SkipType.PASS.getKey(), auditHis.getSkipType());
        assertEquals(TestFlows.HANDLER, auditHis.getApprover());
        assertTrue(auditHis.getMessage().contains("自动审批"), "自动审批应写入办理意见");
        assertEquals(FlowStatus.PASS.getKey(), his(instance.getId(), "apply").getFlowStatus());
    }

    @Test
    void differentHandlerKeepsPendingTaskForNextApprover() {
        TestFlows.autoPassFlow("apOther", AUTO_PASS_ON, TestFlows.HANDLER, OTHER_HANDLER);
        Instance instance = start("biz-ap-other", "apOther");

        Instance after = TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        assertEquals("audit", after.getNodeCode());
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(Set.of("audit"), pending.stream().map(Task::getNodeCode).collect(Collectors.toSet()));
        assertEquals(List.of(OTHER_HANDLER), FlowEngine.userService()
            .getPermission(pending.get(0).getId(), UserType.APPROVAL.getKey()));
        assertTrue(FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .noneMatch(h -> "audit".equals(h.getNodeCode())), "非本人待办不应产生历史记录");
    }

    @Test
    void orSignMultiHandlerNodeAutoPassedAsHandler() {
        TestFlows.autoPassFlow("apMulti", AUTO_PASS_ON, TestFlows.HANDLER
            , TestFlows.HANDLER + "@@" + OTHER_HANDLER);
        Instance instance = start("biz-ap-multi", "apMulti");

        Instance finished = TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        // 或签多办理人节点包含当前办理人：以该办理人身份自动通过，与该办理人手工通过等效
        assertEquals("end", finished.getNodeCode());
        HisTask auditHis = his(instance.getId(), "audit");
        assertEquals(TestFlows.HANDLER, auditHis.getApprover(), "自动审批以当前办理人身份记入历史");
        assertEquals(FlowStatus.AUTO_PASS.getKey(), auditHis.getFlowStatus());
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
    }

    @Test
    void countersignMultiHandlerAutoPassOnlyVotesAsHandler() {
        TestFlows.countersignAutoPassFlow("apCounter", AUTO_PASS_ON, TestFlows.HANDLER
            , TestFlows.HANDLER + "@@" + OTHER_HANDLER);
        Instance instance = start("biz-ap-counter", "apCounter");

        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        // 会签多办理人节点包含当前办理人：只代投该办理人一票，任务保留给其余办理人继续办理
        List<HisTask> auditHisList = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(h -> "audit".equals(h.getNodeCode())).collect(Collectors.toList());
        assertTrue(auditHisList.stream().anyMatch(h -> TestFlows.HANDLER.equals(h.getApprover())
            && FlowStatus.AUTO_PASS.getKey().equals(h.getFlowStatus())), "应只代投当前办理人一票");
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(Set.of("audit"), pending.stream().map(Task::getNodeCode).collect(Collectors.toSet()),
            "会签任务应保留给其余办理人");
        assertEquals(List.of(OTHER_HANDLER), FlowEngine.userService()
            .getPermission(pending.get(0).getId(), UserType.APPROVAL.getKey()));

        // 其余办理人办理后流程正常流转
        Instance finished = TestFlows.pass(pending.get(0).getId(), OTHER_HANDLER);
        assertEquals("end", finished.getNodeCode());
    }

    @Test
    void autoPassDisabledWithoutDefinitionFlag() {
        TestFlows.autoPassFlow("apOffEmpty", null, TestFlows.HANDLER, TestFlows.HANDLER);
        Instance instance = start("biz-ap-off-empty", "apOffEmpty");

        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        assertEquals(Set.of("audit"), TestFlows.pendingTasks(instance.getId()).stream()
            .map(Task::getNodeCode).collect(Collectors.toSet()), "定义未开启自动审批时保持原办理语义");
    }

    @Test
    void autoPassDisabledWhenFlagFalse() {
        TestFlows.autoPassFlow("apOffFalse", "{\"autoPass\":false}", TestFlows.HANDLER, TestFlows.HANDLER);
        Instance instance = start("biz-ap-off-false", "apOffFalse");

        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        assertEquals(Set.of("audit"), TestFlows.pendingTasks(instance.getId()).stream()
            .map(Task::getNodeCode).collect(Collectors.toSet()));
    }

    @Test
    void nonObjectExtTreatedAsDisabled() {
        TestFlows.autoPassFlow("apBadExt", "[{\"code\":\"autoPass\",\"value\":\"true\"}]"
            , TestFlows.HANDLER, TestFlows.HANDLER);
        Instance instance = start("biz-ap-bad-ext", "apBadExt");

        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        assertEquals(Set.of("audit"), TestFlows.pendingTasks(instance.getId()).stream()
            .map(Task::getNodeCode).collect(Collectors.toSet()), "定义配置非对象时按未开启处理");
    }

    @Test
    void autoPassChainsUntilOtherHandler() {
        TestFlows.chainedAutoPassFlow("apChain", AUTO_PASS_ON, TestFlows.HANDLER, TestFlows.HANDLER);
        Instance instance = start("biz-ap-chain", "apChain");

        Instance finished = TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        assertEquals("end", finished.getNodeCode());
        assertEquals(FlowStatus.AUTO_PASS.getKey(), his(instance.getId(), "audit").getFlowStatus());
        assertEquals(FlowStatus.AUTO_PASS.getKey(), his(instance.getId(), "review").getFlowStatus());
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
    }

    @Test
    void autoPassChainStopsAtOtherHandler() {
        TestFlows.chainedAutoPassFlow("apChainStop", AUTO_PASS_ON, TestFlows.HANDLER, OTHER_HANDLER);
        Instance instance = start("biz-ap-chain-stop", "apChainStop");

        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        assertEquals(Set.of("review"), TestFlows.pendingTasks(instance.getId()).stream()
            .map(Task::getNodeCode).collect(Collectors.toSet()));
        assertEquals(FlowStatus.AUTO_PASS.getKey(), his(instance.getId(), "audit").getFlowStatus());
        assertTrue(FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .noneMatch(h -> "review".equals(h.getNodeCode())), "链式推进在他人办理人处停止");
    }

    @Test
    void humanTaskCreatedByServiceAdvanceAlsoAutoPassed() {
        TestFlows.autoPassAfterServiceFlow("apSvc", AUTO_PASS_ON, classConfig());
        Instance instance = start("biz-ap-svc", "apSvc");

        Instance finished = TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        assertEquals("ran", RecordingServiceTaskHandler.LAST_VARIABLES.get("svcFlag"));
        assertEquals("end", finished.getNodeCode());
        assertEquals(FlowStatus.AUTO_PASS.getKey(), his(instance.getId(), "review").getFlowStatus());
        assertTrue(TestFlows.pendingTasks(instance.getId()).isEmpty());
    }

    @Test
    void rejectDoesNotAutoPassReturnedNode() {
        TestFlows.rejectAutoPassFlow("apReject", AUTO_PASS_ON, TestFlows.HANDLER, OTHER_HANDLER);
        Instance instance = start("biz-ap-reject", "apReject");
        // apply(HANDLER) → audit(OTHER)：audit 不包含当前办理人，不自动通过
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        // OTHER 驳回到 apply(HANDLER)：退回产生的新待办不得自动通过，否则等于撤销了本次退回
        TestFlows.reject(TestFlows.currentTask(instance.getId()).getId(), OTHER_HANDLER);

        assertEquals(Set.of("apply"), TestFlows.pendingTasks(instance.getId()).stream()
            .map(Task::getNodeCode).collect(Collectors.toSet()), "退回产生的新待办不参与自动审批");
        assertTrue(FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .noneMatch(h -> "apply".equals(h.getNodeCode())
                && FlowStatus.AUTO_PASS.getKey().equals(h.getFlowStatus())), "退回路径不应写入自动完成历史");
    }

    @Test
    void pendingTaskFromEarlierOperationNotAutoPassed() {
        TestFlows.parallelFlow("apPar");
        Instance instance = TestFlows.startParallelFlow("apPar", "biz-ap-par");

        List<Task> forked = TestFlows.pendingTasks(instance.getId());
        assertEquals(Set.of("a1", "b1"), forked.stream().map(Task::getNodeCode).collect(Collectors.toSet()));
        Task a1 = StreamUtils.filterOne(forked, task -> "a1".equals(task.getNodeCode()));

        TestFlows.pass(a1.getId(), TestFlows.HANDLER);

        assertEquals(Set.of("b1"), TestFlows.pendingTasks(instance.getId()).stream()
            .map(Task::getNodeCode).collect(Collectors.toSet()),
            "已存在的其它分支待办不属于本次操作新建，不参与自动审批");
    }

    /**
     * 以 HANDLER 发起指定流程实例。
     *
     * @param businessId 业务 ID
     * @param flowCode   流程编码
     * @return 启动后的流程实例
     */
    private Instance start(String businessId, String flowCode) {
        return FlowEngine.insService().start(businessId, flowCode, TestFlows.context(TestFlows.HANDLER));
    }

    /**
     * 取实例中指定节点的历史任务。
     *
     * @param instanceId 流程实例 ID
     * @param nodeCode   节点编码
     * @return 历史任务
     */
    private HisTask his(Long instanceId, String nodeCode) {
        return FlowEngine.hisTaskService().getByInsId(instanceId).stream()
            .filter(h -> nodeCode.equals(h.getNodeCode())).findFirst().orElseThrow();
    }

    /**
     * 服务任务 node_config：类服务任务指向记录型处理器。
     *
     * @return node_config JSON
     */
    private static String classConfig() {
        return "{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
            + RecordingServiceTaskHandler.class.getName() + "\"}}";
    }
}
