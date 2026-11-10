package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.HisTask;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.utils.SequenceSignUtil;
import org.dromara.warm.flow.core.utils.TodoQueryUtil;
import org.dromara.warm.flow.core.workflow.command.AddSignerCommand;
import org.dromara.warm.flow.core.workflow.command.DelegateCommand;
import org.dromara.warm.flow.core.workflow.command.JumpCommand;
import org.dromara.warm.flow.core.workflow.command.RemoveSignerCommand;
import org.dromara.warm.flow.core.workflow.command.TransferCommand;
import org.dromara.warm.flow.core.workflow.context.OperatorContext;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组签鉴权、完成判定、顺序可见性与自动审批排除特征测试。
 *
 * @author may
 */
class GroupSignCharacteristicTest {

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
    void parallelGroupAdvancesOnlyAfterEveryGroupPasses() {
        Definition definition = definition("group-parallel");
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal")),
            new Group("finance", List.of("role:finance"))));
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> members.contains("dept:legal") ? List.of("alice", "bob") : List.of("carol"));
        Instance instance = start(definition);
        Task task = current(instance);

        FlowEngine.taskService().execute(task.getId(), context("alice", "dept:legal"), SkipType.PASS.getKey());

        assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size(), "仅完成一个组时节点不得推进");
        assertEquals(List.of("carol"), activeHandlers(task.getId()));
        HisTask partial = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(his -> "group".equals(his.getNodeCode())).findFirst().orElseThrow();
        assertEquals("alice", partial.getApprover());
        assertEquals(SkipType.PASS.getKey(), partial.getSkipType());
        assertEquals(CooperateType.NONE.getKey(), partial.getCooperateType());

        FlowEngine.taskService().execute(task.getId(), context("carol", "role:finance"), SkipType.PASS.getKey());

        assertTrue(FlowEngine.taskService().getByInsId(instance.getId()).isEmpty());
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    @Test
    void sameHandlerCompletesAllMatchedParallelGroups() {
        Definition definition = definition("group-parallel-same-handler");
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("user:alice")),
            new Group("finance", List.of("user:alice"))));
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> List.of("alice"));
        Instance instance = start(definition);

        FlowEngine.taskService().execute(current(instance).getId(), context("alice", "user:alice")
            , SkipType.PASS.getKey());

        assertTrue(FlowEngine.taskService().getByInsId(instance.getId()).isEmpty());
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    @Test
    void sequenceAllowsEveryMemberOfCurrentGroupAndRejectsLaterGroups() {
        Definition definition = definition("group-sequence");
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", CooperateType.SEQUENCE, List.of(
            new Group("legal", List.of("dept:legal")),
            new Group("finance", List.of("role:finance"))));
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> members.contains("dept:legal") ? List.of("alice", "bob") : List.of("carol"));
        Instance instance = start(definition);
        Task task = current(instance);

        assertEquals(List.of(task.getId()), ids(TodoQueryUtil.listTodoByHandler("alice")));
        assertEquals(List.of(task.getId()), ids(TodoQueryUtil.listTodoByHandler("bob")),
            "同一组其它成员也应在当前位可见");
        assertTrue(TodoQueryUtil.listTodoByHandler("carol").isEmpty(), "后续组不得提前可见");

        assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .execute(task.getId(), context("carol", "role:finance"), SkipType.PASS.getKey()));
        assertEquals(List.of("alice", "bob", "carol"), activeHandlers(task.getId()));

        FlowEngine.taskService().execute(task.getId(), context("bob", "dept:legal"), SkipType.PASS.getKey());
        assertEquals(List.of("carol"), activeHandlers(task.getId()));
        assertEquals(List.of(task.getId()), ids(TodoQueryUtil.listTodoByHandler("carol")));

        FlowEngine.taskService().execute(task.getId(), context("carol", "role:finance"), SkipType.PASS.getKey());
        assertTrue(FlowEngine.taskService().getByInsId(instance.getId()).isEmpty());
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    @Test
    void sequenceWithSameHandlerCompletesOnlyCurrentGroup() {
        Definition definition = definition("group-sequence-same-handler");
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", CooperateType.SEQUENCE, List.of(
            new Group("legal", List.of("user:alice")),
            new Group("finance", List.of("user:alice"))));
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> List.of("alice"));
        Instance instance = start(definition);
        Task task = current(instance);
        assertEquals(List.of(0, 1), seqNos(task.getId()));

        FlowEngine.taskService().execute(task.getId(), context("alice", "user:alice"), SkipType.PASS.getKey());

        assertEquals(List.of(1), seqNos(task.getId()), "顺序模式只完成当前组，后续组保留");
        assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size());

        FlowEngine.taskService().execute(task.getId(), context("alice", "user:alice"), SkipType.PASS.getKey());
        assertTrue(FlowEngine.taskService().getByInsId(instance.getId()).isEmpty());
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    @Test
    void rejectByAnyParallelGroupMemberClearsAllGroupsAndUsesRejectRoute() {
        Definition definition = definition("group-reject");
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "draft", NodeType.BETWEEN.getKey(), TestFlows.HANDLER);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal")),
            new Group("finance", List.of("role:finance"))));
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "draft", NodeType.BETWEEN.getKey());
        skip(definition, "draft", NodeType.BETWEEN.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        rejectSkip(definition, "group", "draft");
        resolver(members -> members.contains("dept:legal") ? List.of("alice", "bob") : List.of("carol"));
        Instance instance = start(definition);
        TestFlows.pass(current(instance).getId(), TestFlows.HANDLER);
        Task task = current(instance);

        FlowEngine.taskService().execute(task.getId(), context("alice", "dept:legal"), SkipType.REJECT.getKey());

        Instance after = harness.insDao.raw(instance.getId());
        assertEquals("draft", after.getNodeCode());
        assertEquals(List.of(TestFlows.HANDLER), activeHandlers(FlowEngine.taskService()
            .getByInsId(instance.getId()).get(0).getId()));
        assertTrue(FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .anyMatch(his -> "group".equals(his.getNodeCode())
                && SkipType.REJECT.getKey().equals(his.getSkipType())));
    }

    @Test
    void groupAuthorizationUsesExactHandlerInsteadOfPermissionIntersection() {
        Definition definition = definition("group-auth");
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal"))));
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> List.of("alice"));
        Instance instance = start(definition);

        FlowEngine.taskService().execute(current(instance).getId(), context("alice", "dept:legal")
            , SkipType.PASS.getKey());

        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());

        Instance nonMemberInstance = start(definition);
        FlowException nonMember = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .execute(current(nonMemberInstance).getId(), context("mallory", "dept:legal")
                , SkipType.PASS.getKey()));
        assertTrue(nonMember.getMessage().contains("请检查当前用户是否有权限"));
        assertEquals(1, FlowEngine.taskService().getByInsId(nonMemberInstance.getId()).size());

        Instance emptyPermissionsInstance = start(definition);
        FlowException emptyPermissions = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .execute(current(emptyPermissionsInstance).getId(), context("alice")
                , SkipType.PASS.getKey()));
        assertTrue(emptyPermissions.getMessage().contains("无法跳转到该节点"));
    }

    @Test
    void ordinaryNodeStillUsesPermissionIntersection() {
        Definition definition = TestFlows.serialFlow("ordinary-auth");
        Instance instance = FlowEngine.insService().start("biz-ordinary-auth", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));

        FlowException error = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .execute(current(instance).getId(), context(TestFlows.HANDLER, "dept:legal")
                , SkipType.PASS.getKey()));

        assertTrue(error.getMessage().contains("无法跳转到该节点"));
        assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size());
    }

    @Test
    void groupTaskIsNeverAutoPassed() {
        Definition definition = definition("group-auto-pass");
        definition.setDefConfig("{\"autoPass\":true}");
        FlowEngine.defService().updateById(definition);
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "apply", NodeType.BETWEEN.getKey(), TestFlows.HANDLER);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("user:" + TestFlows.HANDLER))));
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "apply", NodeType.BETWEEN.getKey());
        skip(definition, "apply", NodeType.BETWEEN.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> List.of(TestFlows.HANDLER));
        Instance instance = FlowEngine.insService().start("biz-group-auto-pass", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));

        TestFlows.pass(current(instance).getId(), TestFlows.HANDLER);

        List<Task> pending = FlowEngine.taskService().getByInsId(instance.getId());
        assertEquals(1, pending.size(), "组签节点不得被自动审批代替");
        assertEquals("group", pending.get(0).getNodeCode());
    }

    @Test
    void oldTargetHandlerCommandStillTransfersToSingleTarget() {
        Definition definition = groupFlow("group-transfer-legacy", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal"))));
        resolver(members -> List.of("alice"));
        Instance instance = start(definition);
        Task task = current(instance);

        TransferCommand command = new TransferCommand();
        command.setTaskId(task.getId());
        command.setOperator(new OperatorContext("alice"));
        command.setTargetHandler("dave");
        command.setMessage("转给同事");
        FlowEngine.workflow().transfer(command);

        assertEquals(Set.of("dave"), Set.copyOf(activeHandlers(task.getId())));
        HisTask history = FlowEngine.hisTaskService().getByInsId(instance.getId()).stream()
            .filter(his -> CooperateType.TRANSFER.getKey().equals(his.getCooperateType()))
            .findFirst().orElseThrow();
        assertEquals("alice", history.getApprover());
        assertEquals("dave", history.getCollaborator());
        assertEquals("转给同事", history.getMessage());
    }

    @Test
    void transferAcceptsMatchingLegacyAndNewHandlerListsAndRejectsConflicts() {
        Definition definition = groupFlow("group-transfer-compat", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal"))));
        resolver(members -> List.of("alice"));
        Instance instance = start(definition);
        Task task = current(instance);

        TransferCommand matching = new TransferCommand();
        matching.setTaskId(task.getId());
        matching.setOperator(new OperatorContext("alice"));
        matching.setTargetHandler("dave");
        matching.setTargetHandlers(List.of("dave", "dave"));
        FlowEngine.workflow().transfer(matching);
        assertEquals(Set.of("dave"), Set.copyOf(activeHandlers(task.getId())));

        TransferCommand conflict = new TransferCommand();
        conflict.setTaskId(task.getId());
        conflict.setOperator(new OperatorContext("dave"));
        conflict.setTargetHandler("carol");
        conflict.setTargetHandlers(List.of("erin", "frank"));
        FlowException error = assertThrows(FlowException.class, () -> FlowEngine.workflow().transfer(conflict));

        assertTrue(error.getMessage().contains("转办目标冲突"));
        assertEquals(Set.of("dave"), Set.copyOf(activeHandlers(task.getId())));
    }

    @Test
    void ordinaryNodeRejectsMultipleTransferTargets() {
        Definition definition = TestFlows.serialFlow("ordinary-transfer");
        Instance instance = FlowEngine.insService().start("biz-ordinary-transfer", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        Task task = current(instance);

        TransferCommand command = new TransferCommand();
        command.setTaskId(task.getId());
        command.setOperator(new OperatorContext(TestFlows.HANDLER));
        command.setTargetHandlers(List.of("dave", "erin"));
        FlowException error = assertThrows(FlowException.class, () -> FlowEngine.workflow().transfer(command));

        assertTrue(error.getMessage().contains("只能指定一个办理人"));
        assertEquals(List.of(TestFlows.HANDLER), activeHandlers(task.getId()));
    }

    @Test
    void groupTransferReplacesWholeSlotKeepsSeqNoAndAllowsSecondTransfer() {
        Definition definition = groupFlow("group-transfer", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal")),
            new Group("finance", List.of("role:finance"))));
        resolver(members -> members.contains("dept:legal") ? List.of("alice", "bob") : List.of("carol"));
        Instance instance = start(definition);
        Task task = current(instance);

        transfer(task, "alice", List.of("dave", "erin"));
        List<User> rows = users(task.getId());
        assertEquals(Set.of("dave", "erin", "carol"), Set.copyOf(processedBy(rows)));
        assertEquals(Set.of(0), Set.copyOf(seqNosOf(rows, "dave", "erin")));
        assertEquals(Set.of(1), Set.copyOf(seqNosOf(rows, "carol")));
        assertTrue(rows.stream().filter(user -> Set.of("dave", "erin").contains(user.getProcessedBy()))
            .allMatch(user -> org.dromara.warm.flow.core.enums.UserType.TRANSFER.getKey()
                .equals(user.getType())));

        // 受转人可以再次转办当前槽位；槽位原有成员（含同组 erin）一并转出
        transfer(task, "dave", List.of("gina"));
        assertEquals(Set.of("gina", "carol"), Set.copyOf(activeHandlers(task.getId())));

        FlowEngine.taskService().execute(task.getId(), context("gina", "dept:legal"), SkipType.PASS.getKey());
        assertEquals(Set.of("carol"), Set.copyOf(activeHandlers(task.getId())));

        FlowEngine.taskService().execute(task.getId(), context("carol", "role:finance"), SkipType.PASS.getKey());
        assertEquals("end", harness.insDao.raw(instance.getId()).getNodeCode());
    }

    @Test
    void groupTransferRejectsActiveTargetAndNonCurrentSequenceGroup() {
        Definition definition = groupFlow("group-transfer-guards", CooperateType.SEQUENCE, List.of(
            new Group("legal", List.of("dept:legal")),
            new Group("finance", List.of("role:finance"))));
        resolver(members -> members.contains("dept:legal") ? List.of("alice", "bob") : List.of("carol"));
        Instance instance = start(definition);
        Task task = current(instance);

        FlowException activeTarget = assertThrows(FlowException.class, () -> transfer(task, "alice"
            , List.of("bob")));
        assertTrue(activeTarget.getMessage().contains("已经是转办人"));

        FlowException laterGroup = assertThrows(FlowException.class, () -> transfer(task, "carol"
            , List.of("dave")));
        assertTrue(laterGroup.getMessage().contains("顺序组签只能由当前组办理"));

        transfer(task, "alice", List.of("dave"));
        assertEquals(Set.of("dave", "carol"), Set.copyOf(activeHandlers(task.getId())));
    }

    @Test
    void groupRejectsAddRemoveSignerDelegateAndJump() {
        Definition definition = groupFlow("group-actions", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal"))));
        resolver(members -> List.of("alice"));
        Instance instance = start(definition);
        Task task = current(instance);

        AddSignerCommand addSigner = new AddSignerCommand();
        addSigner.setTaskId(task.getId());
        addSigner.setOperator(new OperatorContext("alice"));
        addSigner.setTargetHandlers(List.of("dave"));
        FlowException addError = assertThrows(FlowException.class, () -> FlowEngine.workflow().addSigner(addSigner));
        assertTrue(addError.getMessage().contains("仅支持转办"));

        RemoveSignerCommand removeSigner = new RemoveSignerCommand();
        removeSigner.setTaskId(task.getId());
        removeSigner.setOperator(new OperatorContext("alice"));
        removeSigner.setTargetHandlers(List.of("alice"));
        assertThrows(FlowException.class, () -> FlowEngine.workflow().removeSigner(removeSigner));

        DelegateCommand delegate = new DelegateCommand();
        delegate.setTaskId(task.getId());
        delegate.setOperator(new OperatorContext("alice"));
        delegate.setTargetHandler("dave");
        assertThrows(FlowException.class, () -> FlowEngine.workflow().delegate(delegate));

        JumpCommand jump = new JumpCommand();
        jump.setTaskId(task.getId());
        jump.setOperator(new OperatorContext("alice"));
        jump.setTargetNodeCode("end");
        FlowException jumpError = assertThrows(FlowException.class, () -> FlowEngine.workflow().jump(jump));
        assertTrue(jumpError.getMessage().contains("组签节点不支持跳转"));

        WorkflowContext direct = context("alice", "dept:legal");
        direct.setTargetNodeCode("end");
        assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .execute(task.getId(), direct, SkipType.PASS.getKey()));

        assertEquals(List.of("alice"), activeHandlers(task.getId()));
    }

    @Test
    void groupWithoutPermissionHandlerDoesNotAuthorizeTypedDepartmentToken() {
        Definition definition = groupFlow("group-no-handler", CooperateType.PARALLEL, List.of(
            new Group("legal", List.of("dept:legal"))));
        Instance instance = start(definition);
        Task task = current(instance);

        FlowException emptyPermissions = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .execute(task.getId(), context("alice"), SkipType.PASS.getKey()));
        assertTrue(emptyPermissions.getMessage().contains("无法跳转到该节点"));

        FlowException typedToken = assertThrows(FlowException.class, () -> FlowEngine.taskService()
            .execute(task.getId(), context("alice", "dept:legal"), SkipType.PASS.getKey()));
        assertTrue(typedToken.getMessage().contains("请检查当前用户是否有权限"));

        assertEquals(1, FlowEngine.taskService().getByInsId(instance.getId()).size());
    }

    @Test
    void currentGroupComparesHandlerSlotWithMinimumSlot() {
        List<User> users = List.of(user("alice", 0), user("bob", 0), user("carol", 1));

        assertTrue(SequenceSignUtil.isCurrentGroup(users, "alice"));
        assertTrue(SequenceSignUtil.isCurrentGroup(users, "bob"));
        assertFalse(SequenceSignUtil.isCurrentGroup(users, "carol"));
        assertFalse(SequenceSignUtil.isCurrentPosition(users, "bob"),
            "既有顺序会签判定保持「最小顺序位单个人」语义");
    }

    private User user(String handler, Integer seqNo) {
        return FlowEngine.newUser().setProcessedBy(handler).setSeqNo(seqNo);
    }

    private Definition groupFlow(String flowCode, String order, List<Group> groups) {
        Definition definition = definition(flowCode);
        node(definition, "start", NodeType.START.getKey(), null);
        node(definition, "group", NodeType.BETWEEN.getKey(), null);
        makeGroup(definition, "group", order, groups);
        node(definition, "end", NodeType.END.getKey(), null);
        skip(definition, "start", NodeType.START.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        return definition;
    }

    private void transfer(Task task, String handler, List<String> targets) {
        TransferCommand command = new TransferCommand();
        command.setTaskId(task.getId());
        command.setOperator(new OperatorContext(handler));
        command.setTargetHandlers(targets);
        FlowEngine.workflow().transfer(command);
    }

    private List<User> users(Long taskId) {
        return FlowEngine.userService().list(FlowEngine.newUser().setAssociated(taskId));
    }

    private List<String> processedBy(List<User> users) {
        return users.stream().map(User::getProcessedBy).collect(Collectors.toList());
    }

    private List<Integer> seqNosOf(List<User> users, String... handlers) {
        List<String> targets = List.of(handlers);
        return users.stream().filter(user -> targets.contains(user.getProcessedBy()))
            .map(User::getSeqNo).collect(Collectors.toList());
    }

    private List<Long> ids(List<Task> tasks) {
        return tasks.stream().map(Task::getId).collect(Collectors.toList());
    }

    private List<Integer> seqNos(Long taskId) {
        return FlowEngine.userService().list(FlowEngine.newUser().setAssociated(taskId)).stream()
            .map(User::getSeqNo).collect(Collectors.toList());
    }

    private List<String> activeHandlers(Long taskId) {
        return FlowEngine.userService().list(FlowEngine.newUser().setAssociated(taskId)).stream()
            .map(User::getProcessedBy).collect(Collectors.toList());
    }

    private void resolver(Function<List<String>, List<String>> resolver) {
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
            public List<String> resolveGroupHandlers(List<String> handlers) {
                return resolver.apply(handlers);
            }
        });
        FlowEngine.initPermissionHandler(null);
    }

    private WorkflowContext context(String handler, String... permissions) {
        WorkflowContext context = new WorkflowContext();
        context.setHandler(handler);
        context.setPermissions(List.of(permissions));
        return context;
    }

    private Definition definition(String flowCode) {
        Definition definition = FlowEngine.newDef().setFlowCode(flowCode).setFlowName(flowCode).setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey()).setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(definition);
        return definition;
    }

    private Node node(Definition definition, String code, Integer type, String permission) {
        Node node = FlowEngine.newNode().setDefinitionId(definition.getId()).setNodeCode(code).setNodeName(code)
            .setNodeType(type).setNodeRatio("0").setPermissionFlag(permission);
        FlowEngine.nodeService().save(node);
        return node;
    }

    private void makeGroup(Definition definition, String code, String order, List<Group> groups) {
        Node group = FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), code);
        String entries = groups.stream().map(item -> "{\"id\":\"" + item.id() + "\",\"members\":["
            + item.members().stream().map(member -> "\"" + member + "\"")
                .collect(Collectors.joining(",")) + "]}").collect(Collectors.joining(","));
        group.setCooperateType(CooperateType.GROUP).setPermissionFlag(null).setNodeRatio("0")
            .setNodeConfig("{\"groupSign\":{\"groups\":[" + entries + "],\"order\":\"" + order + "\"}}");
        FlowEngine.nodeService().updateById(group);
    }

    private void skip(Definition definition, String now, Integer nowType, String next, Integer nextType) {
        Skip skip = FlowEngine.newSkip().setDefinitionId(definition.getId()).setNowNodeCode(now)
            .setNowNodeType(nowType).setNextNodeCode(next).setNextNodeType(nextType)
            .setSkipName(now + "_" + next).setSkipType(SkipType.PASS.getKey());
        FlowEngine.skipService().save(skip);
    }

    private void rejectSkip(Definition definition, String now, String next) {
        Skip skip = FlowEngine.newSkip().setDefinitionId(definition.getId()).setNowNodeCode(now)
            .setNowNodeType(NodeType.BETWEEN.getKey()).setNextNodeCode(next)
            .setNextNodeType(NodeType.BETWEEN.getKey()).setSkipName(now + "_reject_" + next)
            .setSkipType(SkipType.REJECT.getKey());
        FlowEngine.skipService().save(skip);
    }

    private Instance start(Definition definition) {
        return FlowEngine.insService().start("biz-" + definition.getFlowCode(), definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
    }

    private Task current(Instance instance) {
        return FlowEngine.taskService().getByInsId(instance.getId()).get(0);
    }

    private record Group(String id, List<String> members) {
    }
}
