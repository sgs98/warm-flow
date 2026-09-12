package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.MathUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 票签完成条件评估器。
 *
 * <p>规则顺序与原协作处理逻辑保持一致，评估器只负责判断是否达到继续流转条件。</p>
 *
 * @author may
 */
final class TaskCooperationRuleEvaluator {

    /**
     * 按原有判断顺序排列的票签规则。
     */
    private final List<Rule> rules = Arrays.asList(
        new ExpressionRule(), new PassCountRule(), new RejectCountRule(), new PassRatioRule());

    /**
     * 按规则顺序评估票签完成条件。
     *
     * @param context 票签统计上下文
     * @return 是否达到继续流转条件
     */
    boolean evaluate(Context context) {
        for (Rule rule : rules) {
            if (rule.supports(context.nodeRatio)) {
                return rule.matches(context);
            }
        }
        return false;
    }

    private interface Rule {

        /**
         * 判断规则是否适用于当前节点配置。
         */
        boolean supports(String nodeRatio);

        /**
         * 判断本次办理是否满足继续流转条件。
         */
        boolean matches(Context context);
    }

    private static final class ExpressionRule implements Rule {

        @Override
        public boolean supports(String nodeRatio) {
            return CooperateType.isVoteSignDefault(nodeRatio)
                || CooperateType.isVoteSignRejectSpel(nodeRatio);
        }

        @Override
        public boolean matches(Context context) {
            Map<String, Object> variable = MapUtil.clone(context.variable);
            variable.put("skipType", context.skipType);
            variable.put("passNum", context.donePassList.size());
            variable.put("rejectNum", context.doneRejectList.size());
            variable.put("todoNum", context.todoList.size());
            variable.put("allNum", context.allNum);
            variable.put("passList", context.donePassList);
            variable.put("rejectList", context.doneRejectList);
            variable.put("todoList", context.todoList);
            return ExpressionUtil.evalVoteSign(context.nodeRatio, variable);
        }
    }

    private static final class PassCountRule implements Rule {

        @Override
        public boolean supports(String nodeRatio) {
            return CooperateType.isVoteSignPassCount(nodeRatio);
        }

        @Override
        public boolean matches(Context context) {
            String passCount = StringUtils.substring(context.nodeRatio
                , context.nodeRatio.indexOf("=") + 1);
            int count = Integer.parseInt(passCount);
            return (context.isPass && context.donePassList.size() + 1 >= count)
                || (!context.isPass && context.doneRejectList.size() + 1 > context.allNum - count);
        }
    }

    private static final class RejectCountRule implements Rule {

        @Override
        public boolean supports(String nodeRatio) {
            return CooperateType.isVoteSignRejectCount(nodeRatio);
        }

        @Override
        public boolean matches(Context context) {
            String rejectCount = StringUtils.substring(context.nodeRatio
                , context.nodeRatio.indexOf("=") + 1);
            int count = Integer.parseInt(rejectCount);
            return (!context.isPass && context.doneRejectList.size() + 1 >= count)
                || (context.isPass && context.donePassList.size() + 1 > context.allNum - count);
        }
    }

    private static final class PassRatioRule implements Rule {

        @Override
        public boolean supports(String nodeRatio) {
            return true;
        }

        @Override
        public boolean matches(Context context) {
            BigDecimal passRatio = (context.isPass ? BigDecimal.ONE : BigDecimal.ZERO)
                .add(BigDecimal.valueOf(context.donePassList.size()))
                .divide(BigDecimal.valueOf(context.allNum), 4, RoundingMode.HALF_UP)
                .multiply(MathUtil.ONE_HUNDRED);
            BigDecimal rejectRatio = (context.isPass ? BigDecimal.ZERO : BigDecimal.ONE)
                .add(BigDecimal.valueOf(context.doneRejectList.size()))
                .divide(BigDecimal.valueOf(context.allNum), 4, RoundingMode.HALF_UP)
                .multiply(MathUtil.ONE_HUNDRED);
            return (!context.isPass
                && rejectRatio.compareTo(MathUtil.ONE_HUNDRED.subtract(new BigDecimal(context.nodeRatio))) > 0)
                || (context.isPass
                && passRatio.compareTo(new BigDecimal(context.nodeRatio)) >= 0);
        }
    }

    /**
     * 票签规则计算所需的只读数据。
     */
    static final class Context {

        /**
         * 节点票签规则配置。
         */
        private final String nodeRatio;
        /**
         * 当前办理结果对应的跳转类型。
         */
        private final String skipType;
        /**
         * 当前办理是否为通过。
         */
        private final boolean isPass;
        /**
         * 当前任务的总办理人数。
         */
        private final int allNum;
        /**
         * 尚未办理的人员列表。
         */
        private final List<?> todoList;
        /**
         * 已通过的历史任务列表。
         */
        private final List<?> donePassList;
        /**
         * 已驳回的历史任务列表。
         */
        private final List<?> doneRejectList;
        /**
         * 当前流程变量。
         */
        private final Map<String, Object> variable;

        Context(String nodeRatio, String skipType, boolean isPass, int allNum, List<?> todoList
            , List<?> donePassList, List<?> doneRejectList, Map<String, Object> variable) {
            this.nodeRatio = nodeRatio;
            this.skipType = skipType;
            this.isPass = isPass;
            this.allNum = allNum;
            this.todoList = defaultList(todoList);
            this.donePassList = defaultList(donePassList);
            this.doneRejectList = defaultList(doneRejectList);
            this.variable = variable == null ? Collections.<String, Object>emptyMap() : variable;
        }

        /**
         * 将可能为空的统计列表统一为空集合，保持规则计算安全。
         */
        private static List<?> defaultList(List<?> list) {
            return list == null ? Collections.emptyList() : list;
        }
    }
}
