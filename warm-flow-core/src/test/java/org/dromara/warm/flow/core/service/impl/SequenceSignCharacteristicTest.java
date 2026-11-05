package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.dto.FlowCombine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.enums.TaskStatus;
import org.dromara.warm.flow.core.enums.UserType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.strategy.VoteSignStrategy;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.FlowConfigUtil;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 顺序会签（node_ratio = 100@@sequence）特征测试：锁定按 seq_no 逐个办理、
 * 未轮到者不可办、驳回一票否决、加签追加末尾、转办继承顺序位。
 *
 * @author may
 */
class SequenceSignCharacteristicTest {

    /**
     * 顺序会签标记。
     */
    private static final String SEQUENCE_RATIO = "100@@sequence";

    /**
     * 三人顺序会签办理人表达式（书写顺序即办理顺序）。
     */
    private static final String SEQUENCE_USERS = TestFlows.HANDLER + "@@lisi@@wangwu";

    static {
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

    /**
     * 发起并推进到 apply 顺序会签节点，返回该待办。
     */
    private Task atApply(String flowCode, String businessId) {
        return atApply(flowCode, SEQUENCE_RATIO, null, businessId);
    }

    private Task atApply(String flowCode, String nodeRatio, String cooperateType, String businessId) {
        Instance instance = TestFlows.startCooperateFlow(flowCode, nodeRatio, SEQUENCE_USERS
            , cooperateType, businessId);
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);
        return TestFlows.currentTask(instance.getId());
    }

    /**
     * 当前任务办理人 → seq_no 映射（允许 null，用于存量未回填场景）。
     */
    private Map<String, Integer> seqNos() {
        Map<String, Integer> map = new HashMap<>();
        for (User user : harness.userDao.all()) {
            map.put(user.getProcessedBy(), user.getSeqNo());
        }
        return map;
    }

    @Test
    void firstInSequence_onlyCurrentPositionCanComplete() {
        Task task = atApply("sq1", "biz-s1");
        assertEquals(Map.of(TestFlows.HANDLER, 0, "lisi", 1, "wangwu", 2), seqNos());

        // 仅当前位可见：未轮到的办理人待办列表为空
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler(TestFlows.HANDLER).stream().map(Task::getId).toList());
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler("lisi"));
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler("wangwu"));

        // 未轮到的第二位直接办理：拒绝，且不产生任何改动
        FlowException ex = assertThrows(FlowException.class, () -> TestFlows.pass(task.getId(), "lisi"));
        assertEquals(ExceptionCons.NOT_AUTHORITY, ex.getMessage());
        assertNotNull(harness.taskDao.raw(task.getId()), "越序办理不应删除待办");
        assertEquals(Map.of(TestFlows.HANDLER, 0, "lisi", 1, "wangwu", 2), seqNos(), "越序办理不应清理办理人");

        // 当前位办理：任务保留、本人办理人删除、不推进
        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()), "顺序会签未结束前待办应保留");
        assertEquals(Map.of("lisi", 1, "wangwu", 2), seqNos());
        assertEquals("apply", harness.insDao.raw(task.getInstanceId()).getNodeCode());
        // 轮到第二位可见
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler(TestFlows.HANDLER));
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("lisi").stream().map(Task::getId).toList());
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler("wangwu"));
    }

    @Test
    void sequentialCompletion_flowsToEndOnLastPosition() {
        Task task = atApply("sq2", "biz-s2");
        Instance instance = harness.insDao.raw(task.getInstanceId());

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertEquals(Map.of("lisi", 1, "wangwu", 2), seqNos());

        TestFlows.pass(task.getId(), "lisi");
        assertEquals(Map.of("wangwu", 2), seqNos());

        TestFlows.pass(task.getId(), "wangwu");
        assertEquals(0, harness.taskDao.size(), "末位办理后任务应流转删除");
        assertEquals(0, harness.userDao.size(), "任务删除后办理人应清理");
        assertEquals("end", instance.getNodeCode());
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
        // 历史：start 发起 + draft 办理 + 两次签票 + 末位归档 = 5
        assertEquals(5, harness.hisTaskDao.size());
    }

    @Test
    void middleReject_vetoesAndDropsRestUsers() {
        Task task = atApply("sq3", "biz-s3");
        Instance instance = harness.insDao.raw(task.getInstanceId());
        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        TestFlows.reject(task.getId(), "lisi");

        // 一票否决：退回 draft，其余办理人清退，仅新待办的办理人保留
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("draft", pending.get(0).getNodeCode());
        assertEquals(1, harness.userDao.size());
        assertEquals(TestFlows.HANDLER, harness.userDao.all().get(0).getProcessedBy());
        assertEquals(FlowStatus.BACK.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void addSigner_appendsToSequenceEnd() {
        Task task = atApply("sq4", "biz-s4");

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), List.of(), CooperateType.ADD_SIGNER.getKey());
        assertEquals(Map.of(TestFlows.HANDLER, 0, "lisi", 1, "wangwu", 2, "zhaoliu", 3), seqNos());

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        TestFlows.pass(task.getId(), "lisi");
        TestFlows.pass(task.getId(), "wangwu");
        assertNotNull(harness.taskDao.raw(task.getId()), "加签未办理前流程不应推进");
        assertEquals(Map.of("zhaoliu", 3), seqNos());

        TestFlows.pass(task.getId(), "zhaoliu");
        assertEquals(0, harness.taskDao.size());
    }

    @Test
    void transfer_inheritsCurrentSeqNo() {
        Task task = atApply("sq5", "biz-s5");

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), List.of(TestFlows.HANDLER), CooperateType.TRANSFER.getKey());
        List<User> transferred = harness.userDao.all().stream()
            .filter(u -> UserType.TRANSFER.getKey().equals(u.getType())).toList();
        assertEquals(1, transferred.size());
        assertEquals("zhaoliu", transferred.get(0).getProcessedBy());
        assertEquals(0, transferred.get(0).getSeqNo(), "转办应继承当前位顺序号");

        // 转办人接手当前位；原第一位已不在办理人集合中
        TestFlows.pass(task.getId(), "zhaoliu");
        assertNotNull(harness.taskDao.raw(task.getId()));
        assertEquals(Map.of("lisi", 1, "wangwu", 2), seqNos());
    }

    @Test
    void legacyNullSeqNo_orderedById() {
        Task task = atApply("sq9", "biz-s9");
        // 模拟未执行升级脚本的存量数据：直接清空内存行 seq_no（all() 返回存储对象引用），按 id 兜底
        for (User user : harness.userDao.all()) {
            user.setSeqNo(null);
        }

        // 队列仍按建行顺序（id 升序）：zhangsan → lisi → wangwu
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler(TestFlows.HANDLER).stream().map(Task::getId).toList());
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler("lisi"));

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("lisi").stream().map(Task::getId).toList());
        assertNotNull(harness.taskDao.raw(task.getId()));
    }

    @Test
    void autoPass_doesNotSkipSequenceNode() {
        TestFlows.sequenceSignAutoPassFlow("sq7", "{\"autoPass\":true}"
            , TestFlows.HANDLER, SEQUENCE_USERS);
        Instance instance = FlowEngine.insService().start("biz-s7", "sq7"
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        // 自动审批不得代替顺签节点：audit 待办保留，且没有 AUTO_PASS 历史
        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("audit", pending.get(0).getNodeCode());
        assertEquals(0, harness.hisTaskDao.all().stream()
            .filter(h -> "audit".equals(h.getNodeCode()))
            .filter(h -> TaskStatus.AUTO_PASS.getKey().equals(h.getFlowStatus())).count());
        assertEquals(Map.of(TestFlows.HANDLER, 0, "lisi", 1, "wangwu", 2), seqNos());
    }

    @Test
    void sequenceTransferWithNullRemoval_preservesPureAdd() {
        Task task = atApply("sq26", "biz-s26");

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), null, CooperateType.TRANSFER.getKey());

        assertEquals(4, harness.userDao.size());
        assertEquals(1, harness.userDao.all().stream()
            .filter(user -> "zhaoliu".equals(user.getProcessedBy())
                && UserType.TRANSFER.getKey().equals(user.getType())
                && Integer.valueOf(0).equals(user.getSeqNo())).count());
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("zhaoliu").stream().map(Task::getId).toList());
    }

    @Test
    void sequenceDelegateWithNullRemoval_preservesPureAdd() {
        Task task = atApply("sq27", "biz-s27");

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), null, CooperateType.DELEGATE.getKey());

        assertEquals(4, harness.userDao.size());
        assertEquals(1, harness.userDao.all().stream()
            .filter(user -> "zhaoliu".equals(user.getProcessedBy())
                && UserType.DEPUTE.getKey().equals(user.getType())
                && Integer.valueOf(0).equals(user.getSeqNo())).count());
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("zhaoliu").stream().map(Task::getId).toList());
    }

    @Test
    void transferRemoval_requiresActiveOperatorRow() {
        Task task = atApply("sq28", "biz-s28");
        WorkflowContext context = new WorkflowContext();
        context.setHandler("outsider");
        context.setPermissions(List.of(TestFlows.HANDLER));

        FlowException ex = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .updateHandlers(task.getId(), context, List.of("zhaoliu"), List.of("outsider")
                , CooperateType.TRANSFER.getKey()));

        assertEquals(ExceptionCons.NOT_AUTHORITY, ex.getMessage());
        assertEquals(Map.of(TestFlows.HANDLER, 0, "lisi", 1, "wangwu", 2), seqNos());
    }

    @Test
    void nonCurrentSigner_canTransferSelf() {
        Task task = atApply("sq10", "biz-s10");

        // 非当前位办理人转办自己：只替换同一顺序号，不破坏队列
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context("lisi")
            , List.of("zhaoliu"), List.of("lisi"), CooperateType.TRANSFER.getKey());
        assertEquals(Map.of(TestFlows.HANDLER, 0, "zhaoliu", 1, "wangwu", 2), seqNos());
        assertEquals(1, harness.userDao.all().stream()
            .filter(u -> UserType.TRANSFER.getKey().equals(u.getType())).count());

        // 未轮到时依旧不可见、不可办
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler("zhaoliu"));
        FlowException ex = assertThrows(FlowException.class, () -> TestFlows.pass(task.getId(), "zhaoliu"));
        assertEquals(ExceptionCons.NOT_AUTHORITY, ex.getMessage());

        // 轮到第二位时由转办人接替
        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("zhaoliu").stream().map(Task::getId).toList());
        TestFlows.pass(task.getId(), "zhaoliu");
        assertNotNull(harness.taskDao.raw(task.getId()));
        assertEquals(Map.of("wangwu", 2), seqNos());
    }

    @Test
    void nonCurrentDelegate_cannotExecuteEarly() {
        Task task = atApply("sq16", "biz-s16");
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context("lisi")
            , List.of("zhaoliu"), List.of("lisi"), CooperateType.DELEGATE.getKey());
        int historySize = harness.hisTaskDao.size();

        FlowException ex = assertThrows(FlowException.class, () -> TestFlows.pass(task.getId(), "zhaoliu"));

        assertEquals(ExceptionCons.NOT_AUTHORITY, ex.getMessage());
        assertEquals(historySize, harness.hisTaskDao.size(), "越序受托办理不得写入历史");
        assertEquals(1, harness.userDao.all().stream()
            .filter(user -> "zhaoliu".equals(user.getProcessedBy())
                && UserType.DEPUTE.getKey().equals(user.getType())
                && Integer.valueOf(1).equals(user.getSeqNo())).count());
    }

    @Test
    void updateHandlers_outOfSequence_denied() {
        Task task = atApply("sq8", "biz-s8");

        // 未轮到的办理人不可转办 / 加签
        FlowException ex = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .updateHandlers(task.getId(), TestFlows.context("lisi"), List.of("zhaoliu"), List.of()
                , CooperateType.ADD_SIGNER.getKey()));
        assertEquals(ExceptionCons.NOT_AUTHORITY, ex.getMessage());
        assertEquals(Map.of(TestFlows.HANDLER, 0, "lisi", 1, "wangwu", 2), seqNos());
    }

    @Test
    void mixedNullSeqNo_fallsBackToIdForWholeTask() {
        Task task = atApply("sq11", "biz-s11");
        List<User> users = harness.userDao.all();
        users.get(0).setSeqNo(5);
        users.get(1).setSeqNo(null);
        users.get(2).setSeqNo(0);

        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler(TestFlows.HANDLER).stream().map(Task::getId).toList());
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler("wangwu"));

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("lisi").stream().map(Task::getId).toList());
    }

    @Test
    void mixedNullSeqNo_transferKeepsFallbackPosition() {
        Task task = atApply("sq17", "biz-s17");
        List<User> users = harness.userDao.all();
        User first = users.stream()
            .filter(user -> TestFlows.HANDLER.equals(user.getProcessedBy())).findFirst().orElseThrow();
        Long firstId = first.getId();
        first.setSeqNo(5);
        users.stream().filter(user -> "lisi".equals(user.getProcessedBy())).findFirst().orElseThrow().setSeqNo(null);
        users.stream().filter(user -> "wangwu".equals(user.getProcessedBy())).findFirst().orElseThrow().setSeqNo(0);

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), List.of(TestFlows.HANDLER), CooperateType.TRANSFER.getKey());

        User transferred = harness.userDao.all().stream()
            .filter(user -> "zhaoliu".equals(user.getProcessedBy())).findFirst().orElseThrow();
        assertEquals(firstId, transferred.getId(), "混合空序号按 id 兜底时必须保留原顺序位");
        assertEquals(5, transferred.getSeqNo());
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("zhaoliu").stream().map(Task::getId).toList());

        TestFlows.pass(task.getId(), "zhaoliu");
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("lisi").stream().map(Task::getId).toList());
    }

    @Test
    void sequenceTargets_mustBeUniqueAndTransferMustHaveOneTarget() {
        Task task = atApply("sq12", "biz-s12");

        FlowException existing = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER), List.of("wangwu")
                , List.of(TestFlows.HANDLER), CooperateType.TRANSFER.getKey()));
        assertEquals(ExceptionCons.IS_ALREADY_TRANSFER, existing.getMessage());

        FlowException multiple = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER), List.of("zhaoliu", "sunqi")
                , List.of(TestFlows.HANDLER), CooperateType.TRANSFER.getKey()));
        assertEquals(ExceptionCons.SEQUENCE_COOPERATE_ONE_HANDLER, multiple.getMessage());
        assertEquals(Map.of(TestFlows.HANDLER, 0, "lisi", 1, "wangwu", 2), seqNos());
    }

    @Test
    void legacyDuplicateHandler_completesOnlyCurrentPosition() {
        Task task = atApply("sq13", "biz-s13");
        User last = harness.userDao.all().stream()
            .filter(user -> "wangwu".equals(user.getProcessedBy())).findFirst().orElseThrow();
        last.setProcessedBy(TestFlows.HANDLER);

        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        assertNotNull(harness.taskDao.raw(task.getId()));
        assertEquals(2, harness.userDao.size());
        assertEquals(1, harness.userDao.all().stream()
            .filter(user -> TestFlows.HANDLER.equals(user.getProcessedBy())
                && Integer.valueOf(2).equals(user.getSeqNo())).count());
    }

    @Test
    void delegate_restoreKeepsExactSlotWithDuplicateOriginalHandler() {
        Task task = atApply("sq18", "biz-s18");
        User last = harness.userDao.all().stream()
            .filter(user -> "wangwu".equals(user.getProcessedBy())).findFirst().orElseThrow();
        last.setProcessedBy(TestFlows.HANDLER);

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), List.of(TestFlows.HANDLER), CooperateType.DELEGATE.getKey());
        TestFlows.pass(task.getId(), "zhaoliu");

        List<Integer> positions = harness.userDao.all().stream()
            .filter(user -> TestFlows.HANDLER.equals(user.getProcessedBy()))
            .map(User::getSeqNo).sorted().toList();
        assertEquals(List.of(0, 2), positions, "委派回收不得因后续同名办理人而丢失当前顺序位");
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler(TestFlows.HANDLER).stream().map(Task::getId).toList());
    }

    @Test
    void futureDelegateWithSameIdentifier_doesNotInterceptCurrentApproval() {
        Task task = atApply("sq20", "biz-s20");
        User future = harness.userDao.all().stream()
            .filter(user -> "wangwu".equals(user.getProcessedBy())).findFirst().orElseThrow();
        future.setProcessedBy(TestFlows.HANDLER).setType(UserType.DEPUTE.getKey()).setCreateBy("wangwu");

        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        assertEquals(1, harness.userDao.all().stream()
            .filter(user -> TestFlows.HANDLER.equals(user.getProcessedBy())
                && UserType.DEPUTE.getKey().equals(user.getType())
                && Integer.valueOf(2).equals(user.getSeqNo())).count());
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("lisi").stream().map(Task::getId).toList());
    }

    @Test
    void duplicateDelegateIdentifier_restoresCurrentConcreteRow() {
        Task task = atApply("sq19", "biz-s19");
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), List.of(TestFlows.HANDLER), CooperateType.DELEGATE.getKey());
        User future = harness.userDao.all().stream()
            .filter(user -> "wangwu".equals(user.getProcessedBy())).findFirst().orElseThrow();
        future.setProcessedBy("zhaoliu").setType(UserType.DEPUTE.getKey()).setCreateBy("wangwu");

        TestFlows.pass(task.getId(), "zhaoliu");

        assertEquals(1, harness.userDao.all().stream()
            .filter(user -> TestFlows.HANDLER.equals(user.getProcessedBy())
                && UserType.APPROVAL.getKey().equals(user.getType())
                && Integer.valueOf(0).equals(user.getSeqNo())).count());
        assertEquals(1, harness.userDao.all().stream()
            .filter(user -> "zhaoliu".equals(user.getProcessedBy())
                && UserType.DEPUTE.getKey().equals(user.getType())
                && Integer.valueOf(2).equals(user.getSeqNo())).count());
    }

    @Test
    void delegate_keepsSequencePositionWhenReturned() {
        Task task = atApply("sq14", "biz-s14");
        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of("zhaoliu"), List.of(TestFlows.HANDLER), CooperateType.DELEGATE.getKey());
        User delegate = harness.userDao.all().stream()
            .filter(user -> UserType.DEPUTE.getKey().equals(user.getType())).findFirst().orElseThrow();
        assertEquals(0, delegate.getSeqNo());

        TestFlows.pass(task.getId(), "zhaoliu");

        User returned = harness.userDao.all().stream()
            .filter(user -> TestFlows.HANDLER.equals(user.getProcessedBy())).findFirst().orElseThrow();
        assertEquals(0, returned.getSeqNo());
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler(TestFlows.HANDLER).stream().map(Task::getId).toList());
    }

    @Test
    void removeCurrentSigner_advancesToNextPosition() {
        Task task = atApply("sq15", "biz-s15");

        FlowEngine.taskService().updateHandlers(task.getId(), TestFlows.context(TestFlows.HANDLER)
            , List.of(), List.of(TestFlows.HANDLER), CooperateType.REMOVE_SIGNER.getKey());

        assertEquals(Map.of("lisi", 1, "wangwu", 2), seqNos());
        assertEquals(List.of(task.getId()), FlowEngine.taskService()
            .listTodoByHandler("lisi").stream().map(Task::getId).toList());
    }

    @Test
    void sequencePassRatio_flowsEarlyAndDropsFuturePositions() {
        Task task = atApply("sq21", "50", CooperateType.SEQUENCE, "biz-s21");
        Instance instance = harness.insDao.raw(task.getInstanceId());
        assertEquals(List.of(), FlowEngine.taskService().listTodoByHandler("lisi"));
        assertEquals(ExceptionCons.NOT_AUTHORITY,
            assertThrows(FlowException.class, () -> TestFlows.pass(task.getId(), "lisi")).getMessage());

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()));
        assertEquals(Map.of("lisi", 1, "wangwu", 2), seqNos());

        TestFlows.pass(task.getId(), "lisi");
        assertEquals(0, harness.userDao.size(), "票签达标后未轮到的顺序位应清理");
        assertEquals(FlowStatus.FINISHED.getKey(), harness.insDao.raw(instance.getId()).getFlowStatus());
    }

    @Test
    void sequencePassCount_flowsOnThreshold() {
        Task task = atApply("sq22", "passCount=2", CooperateType.SEQUENCE, "biz-s22");

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()));
        TestFlows.pass(task.getId(), "lisi");

        assertEquals(0, harness.taskDao.size());
        assertEquals(0, harness.userDao.size());
    }

    @Test
    void sequenceRejectCount_flowsOnThreshold() {
        Task task = atApply("sq23", "rejectCount=2", CooperateType.SEQUENCE, "biz-s23");
        Instance instance = harness.insDao.raw(task.getInstanceId());

        TestFlows.reject(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()));
        TestFlows.reject(task.getId(), "lisi");

        List<Task> pending = TestFlows.pendingTasks(instance.getId());
        assertEquals(1, pending.size());
        assertEquals("draft", pending.get(0).getNodeCode());
    }

    @Test
    void sequenceExpression_flowsWhenVoteStatisticsMatch() {
        Task task = atApply("sq24", "default@@passNum>=1", CooperateType.SEQUENCE, "biz-s24");

        TestFlows.pass(task.getId(), TestFlows.HANDLER);
        assertNotNull(harness.taskDao.raw(task.getId()));
        TestFlows.pass(task.getId(), "lisi");

        assertEquals(0, harness.taskDao.size());
        assertEquals(0, harness.userDao.size());
    }

    @Test
    void explicitParallel_overridesLegacySequenceSuffix() {
        Task task = atApply("sq25", SEQUENCE_RATIO, CooperateType.PARALLEL, "biz-s25");

        TestFlows.pass(task.getId(), "lisi");

        assertNotNull(harness.taskDao.raw(task.getId()));
        assertEquals(2, harness.userDao.size());
    }

    @Test
    void legacySequenceVoteRatio_isAcceptedOnValidation() {
        Definition def = FlowEngine.newDef()
            .setFlowCode("sq6")
            .setFlowName("顺签校验流程")
            .setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey())
            .setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        Node start = node(def, "start", NodeType.START.getKey(), "0");
        Node apply = node(def, "apply", NodeType.BETWEEN.getKey(), "50@@sequence");
        Node end = node(def, "end", NodeType.END.getKey(), "0");
        start.getSkipList().add(skip(def, "start", "apply"));
        apply.getSkipList().add(skip(def, "apply", "end"));
        FlowCombine combine = new FlowCombine();
        combine.setDefinition(def);
        combine.setAllNodes(List.of(start, apply, end));
        combine.setAllSkips(List.of(start.getSkipList().get(0), apply.getSkipList().get(0)));

        FlowConfigUtil.validateGraph(combine);
    }

    private Node node(Definition def, String code, Integer nodeType, String nodeRatio) {
        return FlowEngine.newNode()
            .setDefinitionId(def.getId())
            .setNodeCode(code)
            .setNodeName(code)
            .setNodeType(nodeType)
            .setNodeRatio(nodeRatio);
    }

    private Skip skip(Definition def, String now, String next) {
        return FlowEngine.newSkip()
            .setDefinitionId(def.getId())
            .setNowNodeCode(now)
            .setNextNodeCode(next)
            .setSkipType(SkipType.PASS.getKey());
    }
}
