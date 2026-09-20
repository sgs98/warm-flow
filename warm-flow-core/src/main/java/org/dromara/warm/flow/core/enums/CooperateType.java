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
import org.dromara.warm.flow.core.constant.FlowCons;
import org.dromara.warm.flow.core.utils.MathUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

/**
 * 任务协作类型。
 * <p>
 * 用于标识普通审批、转办、委派、会签、票签、加签和减签等协作行为。
 * APPROVAL-无：无其他协作方式
 * TRANSFER-转办：任务转给其他人办理
 * DEPUTE-委派：求助其他人审批，然后参照他的意见决定是否审批通过
 * COUNTERSIGN-会签：和其他人一起审批通过，才算通过
 * VOTE-票签：和部分人一起审批，达到一定通过率，才算通过
 * ADD_SIGNATURE-加签：办理中途，希望其他人一起参与办理
 * REDUCTION_SIGNATURE-减签：办理中途，希望某些人不参与办理
 *
 * @author xiarg
 * @since 2024/5/10 16:04
 */
@Getter
@AllArgsConstructor
public enum CooperateType {

    APPROVAL(1, "无"),

    TRANSFER(2, "转办"),

    DEPUTE(3, "委派"),

    COUNTERSIGN(4, "会签"),

    VOTE(5, "票签"),

    ADD_SIGNATURE(6, "加签"),

    REDUCTION_SIGNATURE(7, "减签");

    private final Integer key;
    private final String value;


    /**
     * 票签中的固定通过人数策略前缀
     */
    public final static String PASS_COUNT = "passCount";
    /**
     * 票签中的固定驳回人数策略前缀
     */
    public final static String REJECT_COUNT = "rejectCount";

    /**
     * 顺签
     */
    public final static String SEQUENCE = "sequence";

    /**
     * 根据展示文案获取协作类型 key。
     *
     * @param value 展示文案
     * @return 协作类型 key；未匹配时返回 {@code null}
     */
    public static Integer getKeyByValue(String value) {
        for (CooperateType item : CooperateType.values()) {
            if (item.getValue().equals(value)) {
                return item.getKey();
            }
        }
        return null;
    }

    /**
     * 根据协作类型 key 获取展示文案。
     *
     * @param key 协作类型 key
     * @return 展示文案；未匹配时返回 {@code null}
     */
    public static String getValueByKey(Integer key) {
        for (CooperateType item : CooperateType.values()) {
            if (item.getKey().equals(key)) {
                return item.getValue();
            }
        }
        return null;
    }

    /**
     * 根据协作类型 key 获取枚举。
     *
     * @param key 协作类型 key
     * @return 协作类型枚举；未匹配时返回 {@code null}
     */
    public static CooperateType getByKey(Integer key) {
        for (CooperateType item : CooperateType.values()) {
            if (item.getKey().equals(key)) {
                return item;
            }
        }
        return null;
    }


    /**
     * 判断是否为或签
     *
     * @param ratio 比例
     * @return true：是；false：不是
     */
    public static boolean isOrSign(String ratio) {
        return MathUtil.isZero(ratio);
    }

    /**
     * 判断是否是会签
     *
     * @param ratio 比例
     * @return true：是；false：不是
     */
    public static boolean isCountersign(String ratio) {
        return MathUtil.isHundred(ratio);
    }

    /**
     * 判断是否是票签中通过率策略
     *
     * @param ratio 比例
     * @return true：是；false：不是
     */
    public static boolean isVoteSignPassRatio(String ratio) {
        return MathUtil.isBetweenZeroAndHundred(ratio);
    }

    /**
     * 判断是否是票签中的固定通过人数策略
     *
     * @param passCount 固定通过人数
     * @return true：是；false：不是
     */
    public static boolean isVoteSignPassCount(String passCount) {
        return StringUtils.isNotEmpty(passCount) && passCount.startsWith(PASS_COUNT);
    }

    /**
     * 判断是否是票签中的固定驳回人数策略
     *
     * @param rejectCount 固定驳回人数
     * @return true：是；false：不是
     */
    public static boolean isVoteSignRejectCount(String rejectCount) {
        return StringUtils.isNotEmpty(rejectCount) && rejectCount.startsWith(REJECT_COUNT);
    }

    /**
     * 判断是否是票签中的默认表达式策略
     *
     * @param expression 默认表达式
     * @return true：是；false：不是
     */
    public static boolean isVoteSignDefault(String expression) {
        return StringUtils.isNotEmpty(expression) && expression.startsWith(FlowCons.DEFAULT);
    }

    /**
     * 判断是否是票签中的spel表达式策略
     *
     * @param expression spel表达式
     * @return true：是；false：不是
     */
    public static boolean isVoteSignRejectSpel(String expression) {
        return StringUtils.isNotEmpty(expression) && expression.startsWith(FlowCons.SPEL);
    }

    /**
     * 移除顺签标记，返回真正的协作表达式。
     *
     * @param expression 表达式
     * @return 去除顺签后缀后的表达式
     */
    public static boolean isSequenceSign(String expression) {
        return StringUtils.isNotEmpty(expression) && expression.endsWith(FlowCons.SPLIT_AT + SEQUENCE);
    }

    /**
     * 判断是否是顺签
     *
     * @param expression 表达式
     * @return true：是；false：不是
     */
    public static String removeSequence(String expression) {
        if (isSequenceSign(expression)) {
            return expression.substring(0, expression.lastIndexOf(FlowCons.SPLIT_AT + SEQUENCE));
        }
        return expression;
    }

}
