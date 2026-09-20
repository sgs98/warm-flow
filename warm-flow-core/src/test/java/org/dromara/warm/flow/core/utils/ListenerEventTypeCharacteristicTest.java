package org.dromara.warm.flow.core.utils;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.test.FlowTestHarness;
import org.dromara.warm.flow.core.test.TestFlows;
import org.dromara.warm.flow.core.test.listener.RecordingGlobalListener;
import org.dromara.warm.flow.core.test.listener.RecordingListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 监听器事件类型特征测试。
 *
 * @author may
 */
class ListenerEventTypeCharacteristicTest {

    /** 流程引擎测试环境。 */
    private FlowTestHarness harness;

    /**
     * 初始化测试引擎。
     */
    @BeforeEach
    void setUp() {
        harness = new FlowTestHarness();
    }

    /**
     * 清理测试引擎状态。
     */
    @AfterEach
    void tearDown() {
        harness.close();
    }

    /**
     * 节点监听器和全局监听器应读取当前实际触发的事件类型。
     */
    @Test
    void listenerContext_containsActualEventType() {
        Instance instance = TestFlows.start("listener-event", "biz-listener-event");
        harness.reset();

        Task task = TestFlows.currentTask(instance.getId());
        TestFlows.pass(task.getId(), TestFlows.HANDLER);

        assertTrue(RecordingListener.EVENTS.stream().anyMatch(event -> event.contains("event=start")));
        assertTrue(RecordingListener.EVENTS.stream().anyMatch(event -> event.contains("event=assignment")));
        assertTrue(RecordingListener.EVENTS.stream().anyMatch(event -> event.contains("event=finish")));
        assertTrue(RecordingGlobalListener.EVENTS.stream().anyMatch(event -> event.contains("event=start")));
        assertTrue(RecordingGlobalListener.EVENTS.stream().anyMatch(event -> event.contains("event=assignment")));
        assertTrue(RecordingGlobalListener.EVENTS.stream().anyMatch(event -> event.contains("event=finish")));
    }
}
