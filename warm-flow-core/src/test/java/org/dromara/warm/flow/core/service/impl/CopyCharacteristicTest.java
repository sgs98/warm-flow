package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.copy.CopyConfig;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.dromara.warm.flow.core.workflow.command.CopyCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.result.WorkflowResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 抄送特征测试：锁定「到达节点自动抄送 / 手动追加抄送」两条入口的留痕语义——
 * 只写 {@code flow_his_task}（flow_status = copy、cooperate_type = none）与
 * {@code flow_user}（type = 4），不产生待办、不改流程状态、不落协作动作，
 * 并验证发起人占位替换与宿主权限标识归一化。
 *
 * @author may
 */
class CopyCharacteristicTest {

    private FlowTestHarness harness;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    /** 抄送配置格式宽松：缺段、类型不符、空集合都视为不抄送 */
    @Test
    void parse_toleratesMissingOrInvalidSection() {
        assertFalse(CopyConfig.parse(null).enabled());
        assertFalse(CopyConfig.parse("").enabled());
        assertFalse(CopyConfig.parse("{}").enabled());
        assertFalse(CopyConfig.parse("{\"copy\":{}}").enabled());
        assertFalse(CopyConfig.parse("{\"copy\":\"zhangsan\"}").enabled());
        assertFalse(CopyConfig.parse("{\"copy\":{\"handlers\":[]}}").enabled());
        assertFalse(CopyConfig.parse("{\"copy\":{\"handlers\":\"zhangsan\"}}").enabled());
        assertFalse(CopyConfig.parse("{\"serviceTask\":{\"type\":\"class\"}}").enabled());
    }

    /** 解析时丢弃空标识并去重，避免同一人收到多条抄送 */
    @Test
    void parse_dropsBlankAndDuplicateHandlers() {
        CopyConfig config = CopyConfig.parse("{\"copy\":{\"handlers\":[\"lisi\",\"\",null,\"lisi\",\"wangwu\"]}}");

        assertTrue(config.enabled());
        assertEquals(List.of("lisi", "wangwu"), config.getHandlers());
    }

    /** 到达节点自动抄送：每个抄送人一条留痕，抄送不产生待办 */
    @Test
    void autoCopyOnArrival_writesHisTaskAndUserRows() {
        Instance instance = startWithCopy("cp1", "{\"copy\":{\"handlers\":[\"lisi\",\"wangwu\",\"lisi\"]}}");
        Task task = TestFlows.currentTask(instance.getId());

        List<HisTask> copies = copyHisTasks();
        assertEquals(1, copies.size(), "同一次到达只应写一条抄送历史");
        HisTask copy = copies.get(0);
        assertEquals(task.getId(), copy.getTaskId());
        assertEquals("apply", copy.getNodeCode());
        assertEquals(CooperateType.NONE.getKey(), copy.getCooperateType(), "抄送不是协作动作");
        assertEquals(1, harness.taskDao.size(), "抄送不应产生新待办");
        assertEquals(Set.of("lisi", "wangwu"), copyUsers(), "重复的抄送人应去重");
    }

    /** 发起人占位在抄送时替换为流程实例的发起人 */
    @Test
    void autoCopyOnArrival_resolvesInitiatorPlaceholder() {
        Instance instance = startWithCopy("cp2", "{\"copy\":{\"handlers\":[\"warmFlowInitiator\",\"lisi\"]}}");
        TestFlows.currentTask(instance.getId());

        assertEquals(Set.of(TestFlows.HANDLER, "lisi"), copyUsers());
    }

    /** 抄送不改流程状态：待办状态与实例状态前后一致，且不触发额外的流程动作 */
    @Test
    void autoCopy_doesNotChangeInstanceOrTaskStatus() {
        Instance instance = startWithCopy("cp3", "{\"copy\":{\"handlers\":[\"lisi\"]}}");
        Task before = TestFlows.currentTask(instance.getId());
        String insStatusBefore = harness.insDao.raw(instance.getId()).getFlowStatus();
        String taskStatusBefore = harness.taskDao.raw(before.getId()).getFlowStatus();

        // 再手动抄送一次，验证手动入口同样只增留痕
        FlowEngine.taskService().copy(before.getId(), List.of("wangwu"), TestFlows.context(TestFlows.HANDLER));

        assertEquals(insStatusBefore, harness.insDao.raw(instance.getId()).getFlowStatus());
        assertEquals(taskStatusBefore, harness.taskDao.raw(before.getId()).getFlowStatus());
        assertEquals(1, harness.taskDao.size());
        assertEquals(2, copyHisTasks().size(), "自动与手动各写一条抄送历史");
        assertEquals(Set.of("lisi", "wangwu"), copyUsers());
    }

    /** 未配置抄送时不留任何抄送痕迹 */
    @Test
    void noCopyConfig_leavesNoTrace() {
        TestFlows.serialFlow("cp4");
        Instance instance = FlowEngine.insService().start("biz-cp4", "cp4", TestFlows.context(TestFlows.HANDLER));

        assertNotNull(TestFlows.currentTask(instance.getId()));
        assertTrue(copyHisTasks().isEmpty());
        assertTrue(copyUsers().isEmpty());
    }

    /** 门面抄送：返回结果携带实例与任务主键 */
    @Test
    void manualCopy_viaFacade_appendsTrace() {
        TestFlows.serialFlow("cp5");
        Instance instance = FlowEngine.insService().start("biz-cp5", "cp5", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());

        CopyCommand command = new CopyCommand();
        command.setTaskId(task.getId());
        command.setTargetHandlers(List.of("lisi"));
        command.setOperator(new OperatorContext(TestFlows.HANDLER));
        WorkflowResult result = FlowEngine.workflow().copy(command);

        assertTrue(result.isSuccess());
        assertEquals(instance.getId(), result.getInstanceId());
        assertEquals(task.getId(), result.getCompletedTaskId());
        assertEquals(1, copyHisTasks().size());
        assertEquals(Set.of("lisi"), copyUsers());
    }

    /** 抄送人必填：空集合直接拒绝，避免落一条没有接收人的空留痕 */
    @Test
    void manualCopy_rejectsEmptyHandlers() {
        TestFlows.serialFlow("cp6");
        Instance instance = FlowEngine.insService().start("biz-cp6", "cp6", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());

        assertThrows(FlowException.class,
                () -> FlowEngine.taskService().copy(task.getId(), List.of(), TestFlows.context(TestFlows.HANDLER)));
        assertThrows(FlowException.class,
                () -> FlowEngine.taskService().copy(null, List.of("lisi"), TestFlows.context(TestFlows.HANDLER)));
        assertTrue(copyHisTasks().isEmpty());
    }

    /** 抄送人与办理人共用同一套标识归一化：宿主机可把 user:xxx 换成真正的用户标识 */
    @Test
    void copy_normalizesHandlersThroughPermissionHandler() {
        harness.register(PermissionHandler.class, new PermissionHandler() {
            @Override
            public List<String> permissions() {
                return List.of(TestFlows.HANDLER);
            }

            @Override
            public String getHandler() {
                return TestFlows.HANDLER;
            }

            @Override
            public List<String> convertPermissions(List<String> permissions) {
                return permissions.stream()
                        .map(permission -> permission.startsWith("user:")
                                ? permission.substring("user:".length()) : permission)
                        .collect(Collectors.toList());
            }
        });
        FlowEngine.initPermissionHandler(null);

        Instance instance = startWithCopy("cp7", "{\"copy\":{\"handlers\":[\"user:lisi\",\"wangwu\"]}}");
        TestFlows.currentTask(instance.getId());

        assertEquals(Set.of("lisi", "wangwu"), copyUsers(), "user: 前缀应由宿主归一化后落库");
    }

    /** 抄送必须落到具体人：宿主展开不到的标识直接丢弃，不留一条谁都看不见的留痕 */
    @Test
    void autoCopy_dropsHandlersHostCannotResolve() {
        harness.register(PermissionHandler.class, new PermissionHandler() {
            @Override
            public List<String> permissions() {
                return List.of(TestFlows.HANDLER);
            }

            @Override
            public String getHandler() {
                return TestFlows.HANDLER;
            }

            @Override
            public List<String> resolveCopyHandlers(List<String> handlers) {
                // 只认 lisi：部门 / 角色这类展开不到人的标识返回空
                return handlers.contains("lisi") ? List.of("lisi") : List.of();
            }
        });
        FlowEngine.initPermissionHandler(null);

        Instance instance = startWithCopy("cp8", "{\"copy\":{\"handlers\":[\"lisi\",\"dept:audit\"]}}");
        TestFlows.currentTask(instance.getId());

        assertEquals(Set.of("lisi"), copyUsers(), "展开不到人的标识不应落库");
    }

    /** 手动抄送时标识全都落不到人，必须报错而不是静默成功 */
    @Test
    void manualCopy_rejectsHandlersNotResolvedToUsers() {
        harness.register(PermissionHandler.class, new PermissionHandler() {
            @Override
            public List<String> permissions() {
                return List.of(TestFlows.HANDLER);
            }

            @Override
            public String getHandler() {
                return TestFlows.HANDLER;
            }

            @Override
            public List<String> resolveCopyHandlers(List<String> handlers) {
                return List.of();
            }
        });
        FlowEngine.initPermissionHandler(null);

        TestFlows.serialFlow("cp9");
        Instance instance = FlowEngine.insService().start("biz-cp9", "cp9", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());

        assertThrows(FlowException.class, () -> FlowEngine.taskService()
                .copy(task.getId(), List.of("dept:audit"), TestFlows.context(TestFlows.HANDLER)));
        assertTrue(copyHisTasks().isEmpty(), "解析不到人时不应落抄送历史");
    }

    /**
     * 抄送跟着流程留档：待办办理完、待办与办理人一起删除后，抄送人记录仍在。
     *
     * <p>抄送人记录挂在抄送历史主键（{@code flow_his_task.id}）而不是待办主键上——
     * 待办办理完 / 撤回 / 终止会被连同办理人整批删除，按待办 id 关联会让抄送在办结瞬间消失。</p>
     */
    @Test
    void copyTrace_survivesTaskCompletion() {
        Instance instance = startWithCopy("cp10", "{\"copy\":{\"handlers\":[\"lisi\"]}}");
        Task task = TestFlows.currentTask(instance.getId());
        HisTask copy = copyHisTasks().get(0);

        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        assertTrue(harness.taskDao.all().stream().noneMatch(item -> task.getId().equals(item.getId())),
                "待办应已随办理完成删除");
        assertTrue(harness.userDao.all().stream()
                .anyMatch(user -> UserType.COPY.getKey().equals(user.getType())
                        && copy.getId().equals(user.getAssociated())),
                "抄送人应挂在抄送历史主键上，不随待办删除");
    }

    /**
     * 办结之后还能补抄送：待办行已随办理删除，节点信息从归档历史还原。
     *
     * <p>「办完顺手抄送」是最常见的用法，宿主也常把抄送放在办理之后调用；
     * 如果要求待办必须还活着，这条路径会直接报 NOT_FOUNT_TASK。</p>
     */
    @Test
    void manualCopy_afterTaskCompleted_remainsAvailable() {
        TestFlows.serialFlow("cp11");
        Instance instance = FlowEngine.insService().start("biz-cp11", "cp11", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertTrue(harness.taskDao.all().stream().noneMatch(item -> task.getId().equals(item.getId())),
                "待办应已随办理完成删除");

        HisTask hisTask = FlowEngine.taskService()
                .copy(task.getId(), List.of("lisi"), TestFlows.context(TestFlows.HANDLER));

        assertNotNull(hisTask, "办结后补抄送应从归档历史还原出节点信息");
        assertEquals(task.getId(), hisTask.getTaskId());
        assertEquals("apply", hisTask.getNodeCode());
        assertEquals(instance.getId(), hisTask.getInstanceId());
        assertEquals(Set.of("lisi"), copyUsers());
    }

    /**
     * 门面抄送同样支持办结后补抄送：门面不能自己先断言待办还活着，
     * 否则 {@code TaskService#copy} 的归档还原兜底永远走不到。
     */
    @Test
    void facadeCopy_afterTaskCompleted_remainsAvailable() {
        TestFlows.serialFlow("cp13");
        Instance instance = FlowEngine.insService().start("biz-cp13", "cp13", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());

        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        CopyCommand command = new CopyCommand();
        command.setTaskId(task.getId());
        command.setTargetHandlers(List.of("lisi"));
        command.setOperator(new OperatorContext(TestFlows.HANDLER));
        WorkflowResult result = FlowEngine.workflow().copy(command);

        assertTrue(result.isSuccess());
        assertEquals(instance.getId(), result.getInstanceId(), "办结后门面也应能拼回实例信息");
        assertEquals(instance.getId(), copyHisTasks().get(0).getInstanceId());
        assertEquals(Set.of("lisi"), copyUsers());
    }

    /** 同一待办对同一个人只留一条抄送记录：重复抄送（含重试）不产生重复留痕 */
    @Test
    void manualCopy_isIdempotentPerRecipient() {
        TestFlows.serialFlow("cp12");
        Instance instance = FlowEngine.insService().start("biz-cp12", "cp12", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());

        FlowEngine.taskService().copy(task.getId(), List.of("lisi", "wangwu"), TestFlows.context(TestFlows.HANDLER));
        HisTask duplicated = FlowEngine.taskService()
                .copy(task.getId(), List.of("lisi"), TestFlows.context(TestFlows.HANDLER));

        assertNull(duplicated, "抄送人都已抄送过时不再留痕");
        assertEquals(1, copyHisTasks().size());
        assertEquals(Set.of("lisi", "wangwu"), copyUsers());

        HisTask partial = FlowEngine.taskService()
                .copy(task.getId(), List.of("lisi", "zhaoliu"), TestFlows.context(TestFlows.HANDLER));

        assertNotNull(partial, "有新增抄送人时应只补新增的那部分");
        assertEquals(2, copyHisTasks().size());
        assertEquals(List.of("zhaoliu"), List.of(partial.getCollaborator().split(",")));
        assertEquals(Set.of("lisi", "wangwu", "zhaoliu"), copyUsers());
    }

    /**
     * 构建带抄送配置的流程并启动，首待办停在 apply 节点。
     *
     * @param flowCode 流程编码
     * @param config   apply 节点的 node_config
     * @return 启动后的流程实例
     */
    private Instance startWithCopy(String flowCode, String config) {
        TestFlows.serialFlowWithApplyConfig(flowCode, config);
        return FlowEngine.insService().start("biz-" + flowCode, flowCode, TestFlows.context(TestFlows.HANDLER));
    }

    /**
     * 抄送留痕的结果列固定为 copy，不受调用方上下文里的 taskStatus 影响：
     * 办理链上的自动抄送与办理命令共享同一个上下文，若被它带走，抄送会被标成 pass，
     * 审批记录显示成「通过」，宿主按 copy 查抄送历史也会落空（被抄送人打不开详情）。
     */
    @Test
    void copy_ignoresCallerTaskStatus() {
        TestFlows.serialFlow("cp14");
        Instance instance = FlowEngine.insService().start("biz-cp14", "cp14", TestFlows.context(TestFlows.HANDLER));
        Task task = TestFlows.currentTask(instance.getId());

        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        context.setTaskStatus("biz_custom");
        assertNotNull(FlowEngine.taskService().copy(task.getId(), List.of("lisi"), context));

        List<HisTask> copies = copyHisTasks();
        assertEquals(1, copies.size(), "调用方状态不应让抄送历史丢掉 copy 标识");
        assertEquals(TaskStatus.COPY.getKey(), copies.get(0).getFlowStatus());
    }

    /** 全部抄送历史（flow_status = copy） */
    private List<HisTask> copyHisTasks() {
        return harness.hisTaskDao.all().stream()
                .filter(history -> TaskStatus.COPY.getKey().equals(history.getFlowStatus()))
                .collect(Collectors.toList());
    }

    /** 全部抄送人记录（flow_user.type = 4）的用户标识集合 */
    private Set<String> copyUsers() {
        return harness.userDao.all().stream()
                .filter(user -> UserType.COPY.getKey().equals(user.getType()))
                .map(User::getProcessedBy)
                .collect(Collectors.toSet());
    }
}
