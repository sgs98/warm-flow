package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.exception.FlowException;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 网关出口路由特征测试：互斥网关（条件命中优先、未命中回退无条件默认出口、
 * 条件变量缺失抛错）与包容网关（无条件出口始终执行、条件出口命中才加入）。
 * 条件语法为 core 内置的 "eq@@变量|值"（@@ 分隔类型，| 分隔变量与值）。
 *
 * @author warm
 */
class GatewayCharacteristicTest {

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
    void serial_conditionHit_routesToMatchedBranch() {
        Instance instance = TestFlows.startGatewayFlow("gw1", NodeType.SERIAL.getKey()
                , "eq@@route|a", "eq@@route|b", "biz-gw1", Map.of("route", "a"));
        Task apply = TestFlows.currentTask(instance.getId());

        TestFlows.pass(apply.getId(), TestFlows.HANDLER);

        // 命中 a1 条件的出口被选择，另一分支不产生待办
        List<String> pending = TestFlows.pendingTasks(instance.getId()).stream()
                .map(Task::getNodeCode).toList();
        assertEquals(List.of("a1"), pending);
    }

    @Test
    void serial_conditionMiss_fallsToUnconditionalDefault() {
        Instance miss = TestFlows.startGatewayFlow("gw2", NodeType.SERIAL.getKey()
                , "eq@@route|a", null, "biz-gw2", Map.of("route", "x"));
        TestFlows.pass(TestFlows.currentTask(miss.getId()).getId(), TestFlows.HANDLER);
        List<String> missPending = TestFlows.pendingTasks(miss.getId()).stream()
                .map(Task::getNodeCode).toList();
        assertEquals(List.of("b1"), missPending, "条件未命中时应走无条件默认出口");

        Instance hit = TestFlows.startGatewayFlow("gw2b", NodeType.SERIAL.getKey()
                , "eq@@route|a", null, "biz-gw2b", Map.of("route", "a"));
        TestFlows.pass(TestFlows.currentTask(hit.getId()).getId(), TestFlows.HANDLER);
        List<String> hitPending = TestFlows.pendingTasks(hit.getId()).stream()
                .map(Task::getNodeCode).toList();
        assertEquals(List.of("a1"), hitPending, "条件命中时优先于默认出口");
    }

    @Test
    void serial_missingConditionVariable_throws() {
        Instance instance = TestFlows.startGatewayFlow("gw3", NodeType.SERIAL.getKey()
                , "eq@@route|a", "eq@@route|b", "biz-gw3", null);
        Task apply = TestFlows.currentTask(instance.getId());

        // 条件引用的变量不存在：策略前置校验直接抛错，而非按未命中处理
        FlowException ex = assertThrows(FlowException.class,
                () -> TestFlows.pass(apply.getId(), TestFlows.HANDLER));
        assertEquals(ExceptionCons.NULL_CONDITION_VALUE, ex.getMessage());
    }

    @Test
    void inclusive_twoUnconditionalExits_activatesBothBranches() {
        Instance instance = TestFlows.startGatewayFlow("gw4", NodeType.INCLUSIVE.getKey()
                , null, null, "biz-gw4", null);
        TestFlows.pass(TestFlows.currentTask(instance.getId()).getId(), TestFlows.HANDLER);

        // 包容网关无条件出口始终执行：双分支同时产生待办
        Set<String> pending = TestFlows.pendingTasks(instance.getId()).stream()
                .map(Task::getNodeCode).collect(Collectors.toSet());
        assertEquals(Set.of("a1", "b1"), pending);
    }

    @Test
    void inclusive_conditionalExit_joinsOnlyWhenHit() {
        Instance hit = TestFlows.startGatewayFlow("gw5", NodeType.INCLUSIVE.getKey()
                , null, "eq@@route|b", "biz-gw5", Map.of("route", "b"));
        TestFlows.pass(TestFlows.currentTask(hit.getId()).getId(), TestFlows.HANDLER);
        Set<String> hitPending = TestFlows.pendingTasks(hit.getId()).stream()
                .map(Task::getNodeCode).collect(Collectors.toSet());
        assertEquals(Set.of("a1", "b1"), hitPending, "条件命中的出口与无条件出口同时激活");

        Instance miss = TestFlows.startGatewayFlow("gw5b", NodeType.INCLUSIVE.getKey()
                , null, "eq@@route|b", "biz-gw5b", Map.of("route", "a"));
        TestFlows.pass(TestFlows.currentTask(miss.getId()).getId(), TestFlows.HANDLER);
        Set<String> missPending = TestFlows.pendingTasks(miss.getId()).stream()
                .map(Task::getNodeCode).collect(Collectors.toSet());
        assertEquals(Set.of("a1"), missPending, "条件未命中的出口不激活，仅剩无条件出口");
    }
}
