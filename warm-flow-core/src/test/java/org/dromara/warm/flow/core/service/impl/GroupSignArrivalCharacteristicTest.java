package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.EventCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.EventWait;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.enums.EventType;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.PublishStatus;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.service.event.EventPublisher;
import org.dromara.warm.flow.core.spi.GlobalListener;
import org.dromara.warm.flow.core.spi.ListenerVariable;
import org.dromara.warm.flow.core.spi.PermissionHandler;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingGlobalListener;
import org.dromara.warm.flow.core.test.listener.RecordingServiceTaskHandler;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组签办理人解析与到达写前失败特征测试。
 *
 * @author may
 */
class GroupSignArrivalCharacteristicTest {

    private FlowTestHarness harness;
    private AtomicInteger publishedMessages;

    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
        publishedMessages = new AtomicInteger();
        harness.register(EventPublisher.class, message -> publishedMessages.incrementAndGet());
    }

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void groupRowsUseConcreteHandlersAndSharedGroupSequence() {
        Definition definition = definition("group-users");
        Node group = groupNode(definition, "group", List.of(
            new Group("legal", List.of("dept:legal", "dept:legal")),
            new Group("finance", List.of("role:finance"))));
        Instance instance = instance(definition, "group");
        Task task = FlowEngine.taskService().addTask(group, instance, definition
            , TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey());
        resolver(members -> members.contains("dept:legal")
            ? List.of("alice", "bob", "alice") : List.of("carol"));

        List<User> users = FlowEngine.userService().taskAddUsers(List.of(task));

        assertEquals(List.of("alice", "bob", "carol"), users.stream()
            .map(User::getProcessedBy).collect(Collectors.toList()));
        assertEquals(List.of(0, 0, 1), users.stream().map(User::getSeqNo).collect(Collectors.toList()));
    }

    @Test
    void duplicateMemberIsRemovedOnlyInsideItsGroup() {
        Definition definition = definition("group-duplicates");
        Node group = groupNode(definition, "group", List.of(
            new Group("legal", List.of("user:alice", "user:alice")),
            new Group("finance", List.of("user:alice"))));
        Instance instance = instance(definition, "group");
        Task task = FlowEngine.taskService().addTask(group, instance, definition
            , TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey());
        resolver(members -> List.of("alice", "alice"));

        List<User> users = FlowEngine.userService().taskAddUsers(List.of(task));

        assertEquals(List.of("alice", "alice"), users.stream()
            .map(User::getProcessedBy).collect(Collectors.toList()));
        assertEquals(List.of(0, 1), users.stream().map(User::getSeqNo).collect(Collectors.toList()));
    }

    @Test
    void defaultResolverDelegatesIdentityExpansionToHost() {
        PermissionHandler handler = new PermissionHandler() {
            @Override
            public List<String> permissions() {
                return List.of();
            }

            @Override
            public String getHandler() {
                return "operator";
            }

            @Override
            public List<String> convertPermissions(List<String> permissions) {
                return List.of("alice", "role:admin", "dept:legal", "user:bob", "raw-user", " ");
            }
        };

        assertEquals(List.of("alice", "role:admin", "dept:legal", "user:bob", "raw-user", " ")
            , handler.resolveGroupHandlers(List.of("ignored")));
    }

    @Test
    void coreDoesNotInterpretHostResolvedHandlerPrefixes() {
        Definition definition = definition("group-handler-namespace");
        Node group = groupNode(definition, "group", List.of(
            new Group("legal", List.of("dept:legal"))));
        Instance instance = instance(definition, "group");
        Task task = FlowEngine.taskService().addTask(group, instance, definition
            , TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey());
        resolver(members -> List.of(" tenant:user:alice ", "tenant:user:alice", "role:reviewer", " "));

        List<User> users = FlowEngine.userService().taskAddUsers(List.of(task));

        assertEquals(List.of("tenant:user:alice", "role:reviewer"), users.stream()
            .map(User::getProcessedBy).collect(Collectors.toList()));
    }

    @Test
    void startFailureWritesNothingWithoutHostTransaction() {
        Definition definition = simpleFlow("group-start", "group", null);
        makeGroup(definition, "group");
        resolver(members -> List.of());
        Snapshot before = snapshot();

        FlowException error = assertThrows(FlowException.class, () -> FlowEngine.insService()
            .start("biz-group-start", definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER)));

        assertTrue(error.getMessage().contains("legal"));
        assertEquals(before, snapshot());
        assertEquals(0, publishedMessages.get());
    }

    @Test
    void executeFailureKeepsCurrentTaskUsersWaitHistoryAndInstance() {
        Definition definition = simpleFlow("group-execute", "apply", null);
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        FlowEngine.skipService().getByDefIdAndNowNodeCode(definition.getId(), "apply")
            .forEach(skip -> FlowEngine.skipService().removeById(skip.getId()));
        skip(definition, "apply", NodeType.BETWEEN.getKey(), "group", NodeType.BETWEEN.getKey());
        Instance instance = FlowEngine.insService().start("biz-group-execute", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        Task current = current(instance);
        EventWait boundary = waitingBoundary(definition, instance, current);
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> FlowEngine.taskService().execute(current.getId()
            , TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));

        assertEquals(before, snapshot());
        assertEquals(EventCons.STATUS_WAITING, harness.eventWaitDao.raw(boundary.getId()).getStatus());
        assertEquals(0, publishedMessages.get());
    }

    @Test
    void finalGroupPassFailureKeepsCurrentTaskUsersHistoryAndInstance() {
        Definition definition = definition("group-final-pass-failure");
        node(definition, "start", NodeType.START.getKey(), null, null);
        groupNode(definition, "legal", List.of(new Group("legal", List.of("dept:legal"))));
        groupNode(definition, "finance", List.of(new Group("finance", List.of("role:finance"))));
        node(definition, "end", NodeType.END.getKey(), null, null);
        skip(definition, "start", NodeType.START.getKey(), "legal", NodeType.BETWEEN.getKey());
        skip(definition, "legal", NodeType.BETWEEN.getKey(), "finance", NodeType.BETWEEN.getKey());
        skip(definition, "finance", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> members.contains("dept:legal") ? List.of("alice") : List.of("carol"));
        Instance instance = FlowEngine.insService().start("biz-group-final-pass-failure"
            , definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        Task current = current(instance);
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> FlowEngine.taskService().execute(current.getId()
            , TestFlows.context("alice"), SkipType.PASS.getKey()));

        assertEquals(before, snapshot());
    }

    @Test
    void groupRejectFailureKeepsCurrentTaskUsersHistoryAndInstance() {
        Definition definition = definition("group-reject-failure");
        node(definition, "start", NodeType.START.getKey(), null, null);
        groupNode(definition, "legal", List.of(new Group("legal", List.of("dept:legal"))));
        groupNode(definition, "finance", List.of(new Group("finance", List.of("role:finance"))));
        node(definition, "end", NodeType.END.getKey(), null, null);
        skip(definition, "start", NodeType.START.getKey(), "legal", NodeType.BETWEEN.getKey());
        Skip reject = FlowEngine.newSkip().setDefinitionId(definition.getId()).setNowNodeCode("legal")
            .setNowNodeType(NodeType.BETWEEN.getKey()).setNextNodeCode("finance")
            .setNextNodeType(NodeType.BETWEEN.getKey()).setSkipName("legal_reject_finance")
            .setSkipType(SkipType.REJECT.getKey());
        FlowEngine.skipService().save(reject);
        skip(definition, "finance", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> members.contains("dept:legal") ? List.of("alice") : List.of("carol"));
        Instance instance = FlowEngine.insService().start("biz-group-reject-failure"
            , definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER));
        Task current = current(instance);
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> FlowEngine.taskService().execute(current.getId()
            , TestFlows.context("alice"), SkipType.REJECT.getKey()));

        assertEquals(before, snapshot());
    }

    @Test
    void revokeFailureKeepsOldTaskUsersWaitHistoryAndInstance() {
        Definition definition = simpleFlow("group-revoke", "group", null);
        makeGroup(definition, "group");
        resolver(members -> List.of("alice"));
        Instance instance = FlowEngine.insService().start("biz-group-revoke", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        Task current = current(instance);
        EventWait boundary = waitingBoundary(definition, instance, current);
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> FlowEngine.taskService().revoke(instance.getId()
            , TestFlows.context(TestFlows.HANDLER)));

        assertEquals(before, snapshot());
        assertEquals(EventCons.STATUS_WAITING, harness.eventWaitDao.raw(boundary.getId()).getStatus());
    }

    @Test
    void catchArrivalCommitsItsWaitListenersBeforeAssignment() {
        Definition definition = definition("group-catch-listener-order");
        node(definition, "start", NodeType.START.getKey(), null, null);
        node(definition, "fork", NodeType.PARALLEL.getKey(), null, null);
        Node catchNode = node(definition, "catch", NodeType.BETWEEN.getKey(), null
            , EventType.MESSAGE_CATCH.getValue()).setEventName("continue");
        FlowEngine.nodeService().updateById(catchNode);
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        node(definition, "end", NodeType.END.getKey(), null, null);
        skip(definition, "start", NodeType.START.getKey(), "fork", NodeType.PARALLEL.getKey());
        skip(definition, "fork", NodeType.PARALLEL.getKey(), "catch", NodeType.BETWEEN.getKey());
        skip(definition, "fork", NodeType.PARALLEL.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> List.of("alice"));
        RecordingGlobalListener.EVENTS.clear();

        Instance instance = FlowEngine.insService().start("biz-group-catch-listener-order"
            , definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER));

        List<String> events = RecordingGlobalListener.EVENTS;
        int catchStart = indexOfEvent(events, "g:start{node=catch,");
        int catchFinish = indexOfEvent(events, "g:finish{node=catch,");
        int assignment = indexOfEvent(events, "g:assignment{");
        assertTrue(catchStart >= 0 && catchStart < catchFinish);
        assertTrue(catchFinish < assignment, "捕获等待 finish listener 应早于后继 assignment");
        assertEquals("group", current(instance).getNodeCode());
    }

    @Test
    void duplicateRecursiveCatchArrivalCreatesOnlyOneWaitAndListenerPair() {
        Definition definition = definition("group-duplicate-catch");
        node(definition, "start", NodeType.START.getKey(), null, null);
        node(definition, "fork", NodeType.PARALLEL.getKey(), null, null);
        Node throwOne = node(definition, "throw-one", NodeType.BETWEEN.getKey(), null
            , EventType.MESSAGE_THROW.getValue()).setEventName("one");
        Node throwTwo = node(definition, "throw-two", NodeType.BETWEEN.getKey(), null
            , EventType.MESSAGE_THROW.getValue()).setEventName("two");
        Node catchNode = node(definition, "shared-catch", NodeType.BETWEEN.getKey(), null
            , EventType.MESSAGE_CATCH.getValue()).setEventName("shared");
        FlowEngine.nodeService().updateById(throwOne);
        FlowEngine.nodeService().updateById(throwTwo);
        FlowEngine.nodeService().updateById(catchNode);
        skip(definition, "start", NodeType.START.getKey(), "fork", NodeType.PARALLEL.getKey());
        skip(definition, "fork", NodeType.PARALLEL.getKey(), "throw-one", NodeType.BETWEEN.getKey());
        skip(definition, "fork", NodeType.PARALLEL.getKey(), "throw-two", NodeType.BETWEEN.getKey());
        skip(definition, "throw-one", NodeType.BETWEEN.getKey(), "shared-catch", NodeType.BETWEEN.getKey());
        skip(definition, "throw-two", NodeType.BETWEEN.getKey(), "shared-catch", NodeType.BETWEEN.getKey());
        RecordingGlobalListener.EVENTS.clear();
        resolver(members -> List.of("alice"));

        Instance instance = FlowEngine.insService().start("biz-group-duplicate-catch", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));

        assertEquals(1, FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).stream()
            .filter(wait -> "shared-catch".equals(wait.getNodeCode())).count());
        assertEquals(1, RecordingGlobalListener.EVENTS.stream()
            .filter(event -> event.startsWith("g:start{node=shared-catch,")).count());
        assertEquals(1, RecordingGlobalListener.EVENTS.stream()
            .filter(event -> event.startsWith("g:finish{node=shared-catch,")).count());
    }

    @Test
    void catchEventFailureKeepsWaitHistoryAndInstance() {
        Definition definition = simpleFlow("group-catch", "catch", EventType.MESSAGE_CATCH.getValue());
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        skip(definition, "catch", NodeType.BETWEEN.getKey(), "group", NodeType.BETWEEN.getKey());
        Instance instance = FlowEngine.insService().start("biz-group-catch", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        EventWait wait = onlyWait(instance.getId());
        assertEquals("catch", wait.getNodeCode());
        assertEquals("group", FlowEngine.nodeService().getNextNodeList(
            FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), "catch"), null
                , SkipType.PASS.getKey(), null, new org.dromara.warm.flow.core.dto.PathWayData()
                , FlowEngine.defService().getFlowCombineNoDef(definition.getId())).get(0).getNodeCode());
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> new EventRuntimeHandler().handle(wait
            , TestFlows.context(TestFlows.HANDLER)));

        assertEquals(before, snapshot());
        assertEquals(EventCons.STATUS_WAITING, harness.eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void boundaryEventFailureDoesNotInterruptAttachedTask() {
        Definition definition = simpleFlow("group-boundary", "apply", null);
        Node boundary = node(definition, "boundary", NodeType.BETWEEN.getKey(), null
            , EventType.MESSAGE_BOUNDARY.getValue());
        boundary.setEventName("cancel").setAttachedToNodeCode("apply");
        FlowEngine.nodeService().updateById(boundary);
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        skip(definition, "boundary", NodeType.BETWEEN.getKey(), "group", NodeType.BETWEEN.getKey());
        Instance instance = FlowEngine.insService().start("biz-group-boundary", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        EventWait wait = onlyWait(instance.getId());
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> new EventRuntimeHandler().handle(wait
            , TestFlows.context(TestFlows.HANDLER)));

        assertEquals(before, snapshot());
        assertEquals(EventCons.STATUS_WAITING, harness.eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void recursiveMessageEndFailureDoesNotPublishOrWriteEndHistory() {
        Definition definition = simpleFlow("group-message-end", "apply", null);
        Node messageEnd = node(definition, "message-end", NodeType.BETWEEN.getKey(), null
            , EventType.MESSAGE_END.getValue());
        messageEnd.setEventName("approved");
        FlowEngine.nodeService().updateById(messageEnd);
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        FlowEngine.skipService().getByDefIdAndNowNodeCode(definition.getId(), "apply")
            .forEach(skip -> FlowEngine.skipService().removeById(skip.getId()));
        skip(definition, "apply", NodeType.BETWEEN.getKey(), "message-end", NodeType.BETWEEN.getKey());
        skip(definition, "message-end", NodeType.BETWEEN.getKey(), "group", NodeType.BETWEEN.getKey());
        Instance instance = FlowEngine.insService().start("biz-group-message", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> FlowEngine.taskService().execute(current(instance).getId()
            , TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));

        assertEquals(before, snapshot());
        assertEquals(0, publishedMessages.get());
    }

    @Test
    void recursiveTerminateEndFailureDoesNotCancelWaitOrWriteEndHistory() {
        Definition definition = simpleFlow("group-terminate-end", "apply", null);
        node(definition, "terminate-end", NodeType.BETWEEN.getKey(), null
            , EventType.TERMINATE_END.getValue());
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        FlowEngine.skipService().getByDefIdAndNowNodeCode(definition.getId(), "apply")
            .forEach(skip -> FlowEngine.skipService().removeById(skip.getId()));
        skip(definition, "apply", NodeType.BETWEEN.getKey(), "terminate-end", NodeType.BETWEEN.getKey());
        skip(definition, "terminate-end", NodeType.BETWEEN.getKey(), "group", NodeType.BETWEEN.getKey());
        Instance instance = FlowEngine.insService().start("biz-group-terminate", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        EventWait wait = waitingBoundary(definition, instance, current(instance));
        resolver(members -> List.of());
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> FlowEngine.taskService().execute(current(instance).getId()
            , TestFlows.context(TestFlows.HANDLER), SkipType.PASS.getKey()));

        assertEquals(before, snapshot());
        assertEquals(EventCons.STATUS_WAITING, harness.eventWaitDao.raw(wait.getId()).getStatus());
    }

    @Test
    void serviceSuccessorFailureWritesNoEngineRowsAndKeepsExternalHandlerBoundaryVisible() {
        Definition definition = definition("group-service");
        node(definition, "start", NodeType.START.getKey(), null, null);
        Node service = node(definition, "service", NodeType.SERVICE.getKey(), null, null)
            .setNodeConfig("{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
                + RecordingServiceTaskHandler.class.getName() + "\"}}");
        FlowEngine.nodeService().updateById(service);
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        node(definition, "end", NodeType.END.getKey(), null, null);
        skip(definition, "start", NodeType.START.getKey(), "service", NodeType.SERVICE.getKey());
        skip(definition, "service", NodeType.SERVICE.getKey(), "group", NodeType.BETWEEN.getKey());
        skip(definition, "group", NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        resolver(members -> List.of());
        RecordingServiceTaskHandler.LAST_VARIABLES = null;
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> FlowEngine.insService().start("biz-group-service"
            , definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER)));

        assertEquals(before, snapshot());
        assertEquals("ran", RecordingServiceTaskHandler.LAST_VARIABLES.get("svcFlag")
            , "服务处理器是写前预检无法回滚的宿主外部副作用边界");
    }

    @Test
    void multiServiceChainFailureWritesNoRowsForFailingArrival() {
        // 服务处理器执行后才知道后继，整条服务链无法在首个处理器执行前静态预演；
        // 引擎按「每跳到达」保证失败跳零写入，跨跳整体回滚仍由宿主事务承担。
        Definition definition = definition("group-multi-service");
        node(definition, "start", NodeType.START.getKey(), null, null);
        serviceNode(definition, "service-one");
        serviceNode(definition, "service-two");
        node(definition, "group", NodeType.BETWEEN.getKey(), null, null);
        makeGroup(definition, "group");
        skip(definition, "start", NodeType.START.getKey(), "service-one", NodeType.SERVICE.getKey());
        skip(definition, "service-one", NodeType.SERVICE.getKey(), "service-two", NodeType.SERVICE.getKey());
        skip(definition, "service-two", NodeType.SERVICE.getKey(), "group", NodeType.BETWEEN.getKey());
        resolver(members -> List.of());

        FlowException error = assertThrows(FlowException.class, () -> FlowEngine.insService()
            .start("biz-group-multi-service", definition.getFlowCode(), TestFlows.context(TestFlows.HANDLER)));

        assertTrue(error.getMessage().contains("legal"));
        assertTrue(harness.taskDao.all().stream().noneMatch(task -> "group".equals(task.getNodeCode())));
        assertTrue(harness.userDao.all().isEmpty(), "失败到达不得写入办理人");
        assertTrue(harness.eventWaitDao.all().stream().noneMatch(wait -> "group".equals(wait.getNodeCode())));
        assertEquals("ran", RecordingServiceTaskHandler.LAST_VARIABLES.get("svcFlag"));
    }

    @Test
    void revokeRecreatesCatchWaitInsteadOfLeavingNone() {
        Definition definition = simpleFlow("group-revoke-catch", "catch", EventType.MESSAGE_CATCH.getValue());
        Instance instance = FlowEngine.insService().start("biz-group-revoke-catch", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        assertEquals(1, FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId()).size());
        EventWait old = onlyWait(instance.getId());

        FlowEngine.taskService().revoke(instance.getId(), TestFlows.context(TestFlows.HANDLER));

        List<EventWait> active = FlowEngine.eventWaitService().getActiveByInstanceId(instance.getId());
        assertEquals(1, active.size(), "撤回命中同一捕获节点时必须重建等待");
        assertEquals("catch", active.get(0).getNodeCode());
        assertNotEquals(old.getId(), active.get(0).getId(), "旧等待应先取消，再创建新等待");
    }

    @Test
    void restartToGroupFailsBeforeAnyRestartSideEffect() {
        Definition definition = TestFlows.serialFlow("group-restart");
        Instance instance = FlowEngine.insService().start("biz-group-restart", definition.getFlowCode()
            , TestFlows.context(TestFlows.HANDLER));
        TestFlows.pass(current(instance).getId(), TestFlows.HANDLER);
        Node apply = FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), "apply");
        apply.setCooperateType(CooperateType.GROUP).setPermissionFlag(null)
            .setNodeConfig(groupConfig(List.of(new Group("legal", List.of("dept:legal")))));
        FlowEngine.nodeService().updateById(apply);
        EventWait stale = FlowEngine.newEventWait().setDefinitionId(definition.getId())
            .setInstanceId(instance.getId()).setNodeCode("stale")
            .setEventType(EventType.MESSAGE_CATCH.getValue()).setStatus(EventCons.STATUS_WAITING).setVersion(0);
        FlowEngine.dataFillHandler().idFill(stale);
        FlowEngine.eventWaitService().save(stale);
        Snapshot before = snapshot();

        assertThrows(FlowException.class, () -> InstanceRestartCoordinator.restart(instance.getId(), "apply"
            , List.of("alice"), TestFlows.context(TestFlows.HANDLER)));

        assertEquals(before, snapshot());
    }

    private int indexOfEvent(List<String> events, String prefix) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).startsWith(prefix)) {
                return index;
            }
        }
        return -1;
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

    private Definition simpleFlow(String flowCode, String firstCode, String firstEventType) {
        Definition definition = definition(flowCode);
        node(definition, "start", NodeType.START.getKey(), null, null);
        Node first = node(definition, firstCode, NodeType.BETWEEN.getKey()
            , firstEventType == null ? TestFlows.HANDLER : null, firstEventType);
        if (EventType.MESSAGE_CATCH.getValue().equals(firstEventType)) {
            first.setEventName("continue");
            FlowEngine.nodeService().updateById(first);
        }
        node(definition, "end", NodeType.END.getKey(), null, null);
        skip(definition, "start", NodeType.START.getKey(), firstCode, NodeType.BETWEEN.getKey());
        if (!EventType.MESSAGE_CATCH.getValue().equals(firstEventType)) {
            skip(definition, firstCode, NodeType.BETWEEN.getKey(), "end", NodeType.END.getKey());
        }
        return definition;
    }

    private Node serviceNode(Definition definition, String code) {
        Node node = node(definition, code, NodeType.SERVICE.getKey(), null, null)
            .setNodeConfig("{\"serviceTask\":{\"type\":\"class\",\"handler\":\""
                + RecordingServiceTaskHandler.class.getName() + "\"}}");
        FlowEngine.nodeService().updateById(node);
        return node;
    }

    private Definition definition(String flowCode) {
        Definition definition = FlowEngine.newDef().setFlowCode(flowCode).setFlowName(flowCode).setVersion("1")
            .setIsPublish(PublishStatus.PUBLISHED.getKey()).setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        FlowEngine.defService().save(definition);
        return definition;
    }

    private Node node(Definition definition, String code, Integer type, String permission, String eventType) {
        Node node = FlowEngine.newNode().setDefinitionId(definition.getId()).setNodeCode(code).setNodeName(code)
            .setNodeType(type).setNodeRatio("0").setPermissionFlag(permission).setEventType(eventType);
        FlowEngine.nodeService().save(node);
        return node;
    }

    private Node groupNode(Definition definition, String code, List<Group> groups) {
        Node group = node(definition, code, NodeType.BETWEEN.getKey(), null, null);
        group.setCooperateType(CooperateType.GROUP).setNodeConfig(groupConfig(groups));
        FlowEngine.nodeService().updateById(group);
        return group;
    }

    private void makeGroup(Definition definition, String code) {
        Node group = FlowEngine.nodeService().getByDefIdAndNodeCode(definition.getId(), code);
        group.setCooperateType(CooperateType.GROUP).setPermissionFlag(null)
            .setNodeConfig(groupConfig(List.of(new Group("legal", List.of("dept:legal")))));
        FlowEngine.nodeService().updateById(group);
    }

    private String groupConfig(List<Group> groups) {
        String entries = groups.stream().map(group -> "{\"id\":\"" + group.id() + "\",\"members\":["
            + group.members().stream().map(member -> "\"" + member + "\"")
                .collect(Collectors.joining(",")) + "]}").collect(Collectors.joining(","));
        return "{\"groupSign\":{\"groups\":[" + entries + "],\"order\":\"parallel\"}}";
    }

    private Instance instance(Definition definition, String nodeCode) {
        Instance instance = FlowEngine.newIns().setDefinitionId(definition.getId()).setBusinessId("biz")
            .setNodeCode(nodeCode).setNodeName(nodeCode).setNodeType(NodeType.BETWEEN.getKey())
            .setFlowStatus(FlowStatus.WAITING.getKey()).setActivityStatus(ActivityStatus.ACTIVITY.getKey())
            .setCreateBy(TestFlows.HANDLER);
        FlowEngine.dataFillHandler().idFill(instance);
        return instance;
    }

    private void skip(Definition definition, String nowCode, Integer nowType, String nextCode, Integer nextType) {
        Skip skip = FlowEngine.newSkip().setDefinitionId(definition.getId()).setNowNodeCode(nowCode)
            .setNowNodeType(nowType).setNextNodeCode(nextCode).setNextNodeType(nextType)
            .setSkipName(nowCode + "_" + nextCode).setSkipType(SkipType.PASS.getKey());
        FlowEngine.skipService().save(skip);
    }

    private Task current(Instance instance) {
        return FlowEngine.taskService().getByInsId(instance.getId()).get(0);
    }

    private EventWait onlyWait(Long instanceId) {
        return FlowEngine.eventWaitService().getActiveByInstanceId(instanceId).get(0);
    }

    private EventWait waitingBoundary(Definition definition, Instance instance, Task task) {
        EventWait wait = FlowEngine.newEventWait().setDefinitionId(definition.getId())
            .setInstanceId(instance.getId()).setNodeCode("seed-boundary")
            .setEventType(EventType.MESSAGE_BOUNDARY.getValue()).setEventName("seed")
            .setAttachedTaskId(task.getId()).setStatus(EventCons.STATUS_WAITING)
            .setRetryCount(0).setRepeatCount(0).setVersion(0);
        FlowEngine.dataFillHandler().idFill(wait);
        FlowEngine.eventWaitService().save(wait);
        return wait;
    }

    private Snapshot snapshot() {
        return new Snapshot(rows(harness.taskDao.all(), task -> task.getId() + ":" + task.getInstanceId() + ":"
            + task.getNodeCode() + ":" + task.getFlowStatus()),
            rows(harness.userDao.all(), user -> user.getId() + ":" + user.getAssociated() + ":"
                + user.getProcessedBy() + ":" + user.getType() + ":" + user.getSeqNo()),
            rows(harness.eventWaitDao.all(), wait -> wait.getId() + ":" + wait.getInstanceId() + ":"
                + wait.getAttachedTaskId() + ":" + wait.getNodeCode() + ":" + wait.getStatus()),
            rows(harness.hisTaskDao.all(), history -> history.getId() + ":" + history.getInstanceId() + ":"
                + history.getTaskId() + ":" + history.getNodeCode() + ":" + history.getFlowStatus()),
            rows(harness.insDao.all(), instance -> instance.getId() + ":" + instance.getNodeCode() + ":"
                + instance.getFlowStatus() + ":" + instance.getDefJson()));
    }

    private <T> List<String> rows(List<T> values, Function<T, String> mapper) {
        List<String> rows = new ArrayList<>();
        for (T value : values) {
            rows.add(mapper.apply(value));
        }
        rows.sort(Comparator.naturalOrder());
        return Collections.unmodifiableList(rows);
    }

    private record Group(String id, List<String> members) {
    }

    private record Snapshot(List<String> tasks, List<String> users, List<String> waits
        , List<String> histories, List<String> instances) {
    }
}
