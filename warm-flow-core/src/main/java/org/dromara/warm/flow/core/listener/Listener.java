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
 * 节点监听器接口。
 * <p>
 * 节点可配置多个监听器路径和监听器类型，引擎在对应生命周期点构造 {@link ListenerVariable}
 * 并调用该接口。实现类应避免依赖具体框架，若需要容器能力由框架适配层提供。
 *
 * @author warm
 */
public interface Listener extends Serializable {

    /**
     * 开始监听器，任务开始办理时执行
     */
    String LISTENER_START = "start";

    /**
     * 分派监听器，动态修改代办任务信息
     */
    String LISTENER_ASSIGNMENT = "assignment";

    /**
     * 完成监听器，当前任务完成后执行
     */
    String LISTENER_FINISH = "finish";

    /**
     * 创建监听器，任务创建时执行
     */
    String LISTENER_CREATE = "create";

    /**
     * 表单数据加载监听器，1.3.0 内置表单使用
     */
    String LISTENER_FORM_LOAD = "formLoad";

    /**
     * 执行监听器回调。
     *
     * @param variable 监听器上下文变量
     */
    void notify(ListenerVariable variable);
}
