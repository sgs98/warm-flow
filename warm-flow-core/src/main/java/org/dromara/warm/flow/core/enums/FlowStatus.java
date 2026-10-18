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
import org.dromara.warm.flow.core.utils.ObjectUtil;

import java.util.Set;

/**
 * 流程实例状态。
 * <p>
 * 只描述「这一单处于什么状态」；待办与历史任务的办理结果见 {@link TaskStatus}。
 * key 持久化到实例，value 用于展示。
 *
 * @author warm
 * @since 2023/3/31 12:16
 */
@Getter
@AllArgsConstructor
public enum FlowStatus {

    TOBESUBMIT("0", "待提交"),

    APPROVAL("1", "审批中"),

    TERMINATE("4", "终止"),

    NULLIFY("5", "作废"),

    CANCEL("6", "撤销"),

    RETRIEVE("7", "取回"),

    FINISHED("8", "已完成"),

    REJECT("9", "已退回"),

    RE_START("12", "重启"),

    PENDING("13", "暂存");

    private final String key;
    private final String value;

    /**
     * 内置终态编码：实例到此状态后不再允许办理、流转或撤回。
     */
    private static final Set<String> TERMINAL_KEYS = Set.of(
        FINISHED.key, TERMINATE.key, NULLIFY.key, CANCEL.key);

    /**
     * 判断编码是否属于内置终态。
     * <p>
     * 宿主自定义编码的终态判定走 {@code StatusMapper#isTerminal(String)}，
     * 本方法只认内置编码，供默认映射器复用。
     *
     * @param key 状态编码
     * @return 内置终态返回 {@code true}
     */
    public static boolean isTerminalKey(String key) {
        return ObjectUtil.isNotNull(key) && TERMINAL_KEYS.contains(key);
    }

    /**
     * 获取内置终态编码集合。
     *
     * @return 内置终态编码集合，只读
     */
    public static Set<String> terminalKeys() {
        return TERMINAL_KEYS;
    }

    /**
     * 根据展示文案获取状态 key。
     *
     * @param value 展示文案
     * @return 状态 key；未匹配时返回 {@code null}
     */
    public static String getKeyByValue(String value) {
        for (FlowStatus item : FlowStatus.values()) {
            if (item.getValue().equals(value)) {
                return item.getKey();
            }
        }
        return null;
    }

    /**
     * 根据状态 key 获取展示文案。
     *
     * @param key 状态 key
     * @return 展示文案；未匹配时返回 {@code null}
     */
    public static String getValueByKey(String key) {
        for (FlowStatus item : FlowStatus.values()) {
            if (item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 根据状态 key 获取枚举。
     *
     * @param key 状态 key
     * @return 流程状态枚举；未匹配时返回 {@code null}
     */
    public static FlowStatus getByKey(String key) {
        for (FlowStatus item : FlowStatus.values()) {
            if (item.getKey().equals(key)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断是否结束节点
     *
     * @param key 枚举key
     * @return 是已完成状态时返回 {@code true}
     */
    public static Boolean isFinished(String key) {
        return ObjectUtil.isNotNull(key) && (FlowStatus.FINISHED.getKey().equals(key));
    }

}
