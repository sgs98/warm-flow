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
 * 只描述「这一单处于什么状态」，落在 {@code flow_instance.flow_status}；待办与历史任务的办理结果见
 * {@link TaskStatus}（落 {@code flow_task} / {@code flow_his_task}）。两者是<b>不同的表、不同的维度</b>，
 * 各自拥有独立的编码空间，编码互不干扰。
 * <p>
 * 持久化编码直接用<b>小写英文</b>（如 {@code "finish"}），落库即可自解释，
 * 不必再对照数字含义。分组：
 * <ul>
 *   <li>进行中：{@link #DRAFT}、{@link #WAITING}</li>
 *   <li>终态（实例到此不再办理 / 流转 / 撤回）：{@link #TERMINATE}、{@link #INVALID}、
 *       {@link #CANCEL}、{@link #FINISHED}</li>
 *   <li>特殊流转：{@link #BACK}（退回后仍可继续办理）、{@link #RESTART}</li>
 * </ul>
 * key 持久化到实例，value 用于展示。
 *
 * @author warm
 * @since 2023/3/31 12:16
 */
@Getter
@AllArgsConstructor
public enum FlowStatus {

    /**
     * 待提交：实例已创建但尚未提交，落在开始节点。
     */
    DRAFT("draft", "待提交"),

    /**
     * 审批中：实例正常流转中，落在非结束节点。
     */
    WAITING("waiting", "审批中"),

    /**
     * 终止：流程被终止（含终止结束事件、错误边界终止），终态。
     */
    TERMINATE("termination", "终止"),

    /**
     * 作废：实例作废，终态。引擎当前无写入路径，保留编码供宿主直接置位。
     */
    INVALID("invalid", "作废"),

    /**
     * 撤销：发起人撤回流程，终态。
     */
    CANCEL("cancel", "撤销"),

    /**
     * 已完成：流程正常走到结束节点，终态。
     */
    FINISHED("finish", "已完成"),

    /**
     * 已退回：本次办理退回，实例仍可继续办理（非终态）。
     */
    BACK("back", "已退回"),

    /**
     * 重启：流程重启。引擎当前无写入路径，保留编码供宿主直接置位。
     */
    RESTART("restart", "重启");

    private final String key;
    private final String value;

    /**
     * 内置终态编码：实例到此状态后不再允许办理、流转或撤回。
     */
    private static final Set<String> TERMINAL_KEYS = Set.of(
        FINISHED.key, TERMINATE.key, INVALID.key, CANCEL.key);

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
     * 判断是否为「已完成」状态。
     * <p>
     * 只认 {@link #FINISHED} 一个编码——已完成是流程正常走完，不含终止 / 作废 / 撤销。
     * 若需要判断「实例是否已终结」，请改用 {@link #isTerminalKey(String)}
     * （含终止 / 作废 / 撤销 / 已完成）。
     *
     * @param key 枚举 key
     * @return 是已完成状态时返回 {@code true}
     */
    public static Boolean isFinished(String key) {
        return ObjectUtil.isNotNull(key) && (FlowStatus.FINISHED.getKey().equals(key));
    }

}
