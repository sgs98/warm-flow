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
package org.dromara.warm.flow.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流程定义和表单发布状态。
 * <p>
 * key 持久化到定义或表单中，value 用于展示。
 *
 * @author warm
 * @since 2023/3/31 12:16
 */
@Getter
@AllArgsConstructor
public enum PublishStatus {

    EXPIRED(9, "已失效"),

    UNPUBLISHED(0, "未发布"),

    PUBLISHED(1, "已发布");

    private final Integer key;
    private final String value;

    /**
     * 根据展示文案获取发布状态 key。
     *
     * @param value 展示文案
     * @return 发布状态 key；未匹配时返回 {@code null}
     */
    public static Integer getKeyByValue(String value) {
        for (PublishStatus item : PublishStatus.values()) {
            if (item.getValue().equals(value)) {
                return item.getKey();
            }
        }
        return null;
    }

    /**
     * 根据发布状态 key 获取展示文案。
     *
     * @param key 发布状态 key
     * @return 展示文案；未匹配时返回 {@code null}
     */
    public static String getValueByKey(Integer key) {
        for (PublishStatus item : PublishStatus.values()) {
            if (item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 根据发布状态 key 获取枚举。
     *
     * @param key 发布状态 key
     * @return 发布状态枚举；未匹配时返回 {@code null}
     */
    public static PublishStatus getByKey(Integer key) {
        for (PublishStatus item : PublishStatus.values()) {
            if (item.getKey().equals(key)) {
                return item;
            }
        }
        return null;
    }

}
