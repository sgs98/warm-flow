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

import java.io.Serializable;

/**
 * 全局监听器。
 * <p>
 * 全局监听器在整个引擎范围内生效，可监听任务开始、分派、完成和创建等通用事件。
 * 业务方通常通过引擎配置提供一个实现类，用于统一审计、通知或扩展上下文处理。
 *
 * @author warm
 * @since 2024/11/17
 */
public interface GlobalListener extends Serializable {

    /**
     * 开始监听器，任务开始办理时执行。
     *
     * @param listenerVariable 监听器变量
     */
    default void start(ListenerVariable listenerVariable) {

    }

    /**
     * 分派监听器，在任务分派阶段执行，可动态调整待办任务信息。
     *
     * @param listenerVariable 监听器变量
     */
    default void assignment(ListenerVariable listenerVariable) {

    }

    /**
     * 完成监听器，当前任务完成后执行。
     *
     * @param listenerVariable 监听器变量
     */
    default void finish(ListenerVariable listenerVariable) {

    }

    /**
     * 创建监听器，新任务创建时执行。
     *
     * @param listenerVariable 监听器变量
     */
    default void create(ListenerVariable listenerVariable) {

    }

    /**
     * 按监听器类型分发到具体回调方法。
     *
     * @param type             监听器类型
     * @param listenerVariable 监听器变量
     */
    default void notify(String type, ListenerVariable listenerVariable) {
        switch (type) {
            case Listener.LISTENER_START -> start(listenerVariable);
            case Listener.LISTENER_ASSIGNMENT -> assignment(listenerVariable);
            case Listener.LISTENER_FINISH -> finish(listenerVariable);
            case Listener.LISTENER_CREATE -> create(listenerVariable);
        }
    }
}
