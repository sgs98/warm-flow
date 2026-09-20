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
package org.dromara.warm.flow.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 流程图节点或连线的提示内容配置。
 * <p>
 * 该对象只描述展示结构和样式，不参与流程流转判断。
 *
 * @author warm
 * @since 2025/6/5
 */
@Getter
@Setter
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class PromptContent {

    /**
     * 弹窗整体样式配置。
     */
    private Map<String, Object> dialogStyle;

    /**
     * 弹窗中的提示信息项。
     */
    private List<InfoItem> info;

    /**
     * 单条提示信息及其样式。
     */
    @Getter
    @Setter
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InfoItem {

        /**
         * 信息前缀文本。
         */
        private String prefix;

        /**
         * 前缀样式配置。
         */
        private Map<String, Object> prefixStyle;

        /**
         * 信息主体内容。
         */
        private String content;

        /**
         * 内容样式配置。
         */
        private Map<String, Object> contentStyle;

        /**
         * 当前信息行样式配置。
         */
        private Map<String, Object> rowStyle;

    }

}
