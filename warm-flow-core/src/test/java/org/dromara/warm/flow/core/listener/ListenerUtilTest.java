/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.core.listener;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.invoker.FrameInvoker;
import org.dromara.warm.flow.core.spi.GlobalListener;
import org.dromara.warm.flow.core.spi.Listener;
import org.dromara.warm.flow.core.spi.ListenerVariable;
import org.dromara.warm.flow.core.test.entity.TestDefinition;
import org.dromara.warm.flow.core.test.entity.TestInstance;
import org.dromara.warm.flow.core.test.entity.TestNode;
import org.dromara.warm.flow.core.utils.ListenerUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 监听器执行工具验证：
 * 1. 类型数多于路径数时不再数组越界（配对缺失跳过并告警）；
 * 2. 监听器异常向上抛出中断流程（契约语义：监听器参与主流程事务，
 *    静默忽略会导致办理人缺失等下游错误）。
 *
 * @author warm
 * @since 2026/9/26
 */
class ListenerUtilTest {

    private static final String THROWING_LISTENER = "org.dromara.warm.flow.core.listener.ListenerUtilTest$ThrowingListener";
    private static final String OK_LISTENER = "org.dromara.warm.flow.core.listener.ListenerUtilTest$OkListener";
    private static final List<String> invoked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        invoked.clear();
        FrameInvoker.setBeanFunction(clazz -> {
            if (GlobalListener.class.isAssignableFrom(clazz)) {
                return new RecordingGlobalListener();
            }
            if (THROWING_LISTENER.equals(clazz.getName())) {
                return new ThrowingListener();
            }
            if (OK_LISTENER.equals(clazz.getName())) {
                return new OkListener();
            }
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        FrameInvoker.setBeanFunction(null);
    }

    private TestNode node(String listenerTypes, String listenerPaths) {
        TestNode node = new TestNode();
        node.setNodeType(1);
        node.setListenerType(listenerTypes);
        node.setListenerPath(listenerPaths);
        return node;
    }

    private ListenerVariable variable(TestNode node) {
        return new ListenerVariable(new TestDefinition(), new TestInstance(), node, new HashMap<>());
    }

    @Test
    void typeCountExceedsPathCountDoesNotThrow() {
        // 类型数(2)多于路径数(1)：finish 事件按下标取不到路径，修复前抛数组越界
        ListenerVariable variable = variable(node("start,finish", OK_LISTENER));
        assertDoesNotThrow(() -> ListenerUtil.executeFinish(variable));
        assertEquals(0, invoked.size());
    }

    @Test
    void pairedConfigStillExecutes() {
        ListenerVariable variable = variable(node("start,finish", OK_LISTENER + "@@" + OK_LISTENER));
        ListenerUtil.executeFinish(variable);
        assertEquals(1, invoked.size());
    }

    @Test
    void listenerExceptionPropagatesAndStopsGlobalListener() {
        // 契约语义：监听器异常抛出中断流程，全局监听器不再执行
        ListenerVariable variable = variable(node("start", THROWING_LISTENER));
        FlowEngine.initGlobalListener();
        assertThrows(IllegalStateException.class, () -> ListenerUtil.executeStart(variable));
        assertEquals(0, RecordingGlobalListener.invoked.size());
    }

    static class ThrowingListener implements Listener {
        @Override
        public void notify(ListenerVariable variable) {
            throw new IllegalStateException("监听器异常");
        }
    }

    static class OkListener implements Listener {
        @Override
        public void notify(ListenerVariable variable) {
            invoked.add(variable.getEventType());
        }
    }

    static class RecordingGlobalListener implements GlobalListener {
        static final List<String> invoked = new ArrayList<>();

        @Override
        public void start(ListenerVariable listenerVariable) {
            invoked.add("start");
        }
    }
}
