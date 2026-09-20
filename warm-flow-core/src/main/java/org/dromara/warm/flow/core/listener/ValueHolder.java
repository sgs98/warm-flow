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

import lombok.Data;

/**
 * 监听器路径解析结果。
 * <p>
 * 保存监听器类路径、已解析的监听器实例以及路径中携带的参数。
 *
 * @author warm
 */
@Data
public class ValueHolder {

    /**
     * 监听器类路径。
     */
    private String path;

    /**
     * 已解析的监听器实例。
     */
    private Listener listener;

    /**
     * 监听器路径中携带的参数。
     */
    private String params;

}
