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
package org.dromara.warm.flow.core.test.listener;

import org.dromara.warm.flow.core.listener.GlobalListener;
import org.dromara.warm.flow.core.listener.ListenerVariable;

import java.util.function.Consumer;

/**
 * 单测用可写库全局监听器：在 start 触发点执行测试装配的库写钩子，
 * 用于锁定「监听器中途写库不进入本次操作快照」的执行作用域语义（R2/R3/R5）。
 *
 * @author warm
 */
public class MutatingGlobalListener extends RecordingGlobalListener {

    /** start 事件触发的库写钩子，测试在操作前装配、操作后必须清理 */
    public static Consumer<ListenerVariable> onStart;

    private static final GlobalListener INSTANCE = new MutatingGlobalListener();

    private MutatingGlobalListener() {
    }

    public static GlobalListener instance() {
        return INSTANCE;
    }

    @Override
    public void start(ListenerVariable variable) {
        super.start(variable);
        if (onStart != null) {
            onStart.accept(variable);
        }
    }
}
