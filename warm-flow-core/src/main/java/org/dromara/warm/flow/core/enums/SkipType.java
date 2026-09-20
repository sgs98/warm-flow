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
import org.dromara.warm.flow.core.utils.StringUtils;

/**
 * 流程跳转类型。
 * <p>
 * 用于区分通过、退回和无动作等流转动作。
 *
 * @author warm
 * @since 2023/3/31 12:16
 */
@Getter
@AllArgsConstructor
public enum SkipType {

    PASS("PASS", "审批通过"),

    REJECT("REJECT", "退回"),

    NONE("NONE", "无动作");

    private final String key;
    private final String value;

    /**
     * 根据展示文案获取跳转类型 key。
     *
     * @param value 展示文案
     * @return 跳转类型 key；未匹配时返回 {@code null}
     */
    public static String getKeyByValue(String value) {
        for (SkipType item : SkipType.values()) {
            if (item.getValue().equals(value)) {
                return item.getKey();
            }
        }
        return null;
    }

    /**
     * 根据跳转类型 key 获取展示文案。
     *
     * @param key 跳转类型 key
     * @return 展示文案；未匹配时返回 {@code null}
     */
    public static String getValueByKey(String key) {
        for (SkipType item : SkipType.values()) {
            if (item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 根据跳转类型 key 获取枚举。
     *
     * @param key 跳转类型 key
     * @return 跳转类型枚举；未匹配时返回 {@code null}
     */
    public static SkipType getByKey(String key) {
        for (SkipType item : SkipType.values()) {
            if (item.getKey().equals(key)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断是否通过类型
     *
     * @param key 枚举key
     * @return 是通过类型时返回 {@code true}
     */
    public static Boolean isPass(String key) {
        return StringUtils.isNotEmpty(key) && (SkipType.PASS.getKey().equals(key));
    }

    /**
     * 判断是否退回类型
     *
     * @param key 枚举key
     * @return 是退回类型时返回 {@code true}
     */
    public static Boolean isReject(String key) {
        return StringUtils.isNotEmpty(key) && (SkipType.REJECT.getKey().equals(key));
    }

    /**
     * 判断是否无动作类型
     *
     * @param key 枚举key
     * @return 是无动作类型时返回 {@code true}
     */
    public static Boolean isNone(String key) {
        return StringUtils.isNotEmpty(key) && (SkipType.NONE.getKey().equals(key));
    }

}
