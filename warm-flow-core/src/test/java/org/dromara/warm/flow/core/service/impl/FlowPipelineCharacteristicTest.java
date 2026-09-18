package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 流水线机制特征测试：短路截断、链尾返回、空链语义。
 * 步骤顺序与操作行为由各操作特征测试锁定，此处只测编排器机制。
 *
 * @author warm
 */
class FlowPipelineCharacteristicTest {

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
    void run_shortCircuitStopsChainAndPassesInstanceThrough() {
        FlowExecution execution = activeExecution("fpc1");
        List<String> order = new ArrayList<>();
        FlowPipeline pipeline = FlowPipeline.of(
            step(order, "one", null),
            step(order, "two", execution.instance),
            step(order, "three", null));

        Instance result = pipeline.run(execution);

        // 短路：第三个步骤不再执行，返回的实例即短路步骤透传的引用
        assertEquals(List.of("one", "two"), order);
        assertSame(execution.instance, result);
    }

    @Test
    void run_fullChainReturnsExecutionInstance() {
        FlowExecution execution = activeExecution("fpc2");
        List<String> order = new ArrayList<>();
        FlowPipeline pipeline = FlowPipeline.of(
            step(order, "one", null),
            step(order, "two", null),
            step(order, "three", null));

        Instance result = pipeline.run(execution);

        assertEquals(List.of("one", "two", "three"), order);
        assertSame(execution.instance, result);
    }

    @Test
    void run_emptyChainReturnsExecutionInstance() {
        FlowExecution execution = activeExecution("fpc3");

        Instance result = FlowPipeline.of().run(execution);

        assertSame(execution.instance, result);
    }

    /**
     * 构造记录执行顺序的桩步骤；shortCircuit 非空时返回短路结果。
     */
    private FlowStep step(List<String> order, String name, Instance shortCircuit) {
        return execution -> {
            order.add(name);
            return Optional.ofNullable(shortCircuit);
        };
    }

    /**
     * 从激活流程构造任务级执行作用域（guard 表放行，仅为拿到 execution.instance 引用）。
     */
    private FlowExecution activeExecution(String flowCode) {
        Instance instance = TestFlows.start(flowCode, "biz-" + flowCode);
        Task task = TestFlows.currentTask(instance.getId());
        WorkflowContext context = TestFlows.context(TestFlows.HANDLER);
        return FlowExecution.loadTask(task, context, FlowOp.EXECUTE);
    }
}
