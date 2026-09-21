package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.enums.CooperateType;
import org.dromara.warm.flow.core.utils.ExpressionUtil;
import org.dromara.warm.flow.core.utils.MapUtil;
import org.dromara.warm.flow.core.utils.MathUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final List<Rule> rules = List.of(
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

    /**
     * 票签完成条件的内部规则契约。
     */
    private interface Rule {

        /**
         * 判断规则是否适用于当前节点配置。
         *
         * @param nodeRatio 节点票签规则配置
         * @return 是否适用
         */
        boolean supports(String nodeRatio);

        /**
         * 判断本次办理是否满足继续流转条件。
         *
         * @param context 票签统计上下文
         * @return 是否满足继续流转
         */
        boolean matches(Context context);
    }

    /** 使用表达式计算票签是否完成。 */
    private static final class ExpressionRule implements Rule {

        /**
         * 判断是否为默认票签表达式或驳回表达式配置。
         *
         * @param nodeRatio 节点票签规则配置
         * @return 是否由表达式规则处理
         */
        @Override
        public boolean supports(String nodeRatio) {
            return CooperateType.isVoteSignDefault(nodeRatio)
                || CooperateType.isVoteSignRejectSpel(nodeRatio);
        }

        /**
         * 注入票签统计变量并执行表达式。
         *
         * @param context 票签统计上下文
         * @return 表达式是否满足继续流转条件
         */
        @Override
        public boolean matches(Context context) {
            // 表达式使用副本变量，避免把票签统计字段写回流程实例变量。
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

    /** 达到指定通过人数时完成，或在剩余票数已无法通过时提前驳回。 */
    private static final class PassCountRule implements Rule {

        /**
         * 判断是否为通过人数规则。
         *
         * @param nodeRatio 节点票签规则配置
         * @return 是否由通过人数规则处理
         */
        @Override
        public boolean supports(String nodeRatio) {
            return CooperateType.isVoteSignPassCount(nodeRatio);
        }

        /**
         * 计算通过人数是否达标，或驳回人数是否已使通过条件无法达成。
         *
         * @param context 票签统计上下文
         * @return 是否满足继续流转条件
         */
        @Override
        public boolean matches(Context context) {
            // 当前办理结果尚未写入历史列表，因此通过/驳回计数需要加上本次结果。
            String passCount = StringUtils.substring(context.nodeRatio
                , context.nodeRatio.indexOf("=") + 1);
            int count = Integer.parseInt(passCount);
            return (context.isPass && context.donePassList.size() + 1 >= count)
                || (!context.isPass && context.doneRejectList.size() + 1 > context.allNum - count);
        }
    }

    /** 达到指定驳回人数时完成，或在剩余票数已无法驳回时提前通过。 */
    private static final class RejectCountRule implements Rule {

        /**
         * 判断是否为驳回人数规则。
         *
         * @param nodeRatio 节点票签规则配置
         * @return 是否由驳回人数规则处理
         */
        @Override
        public boolean supports(String nodeRatio) {
            return CooperateType.isVoteSignRejectCount(nodeRatio);
        }

        /**
         * 计算驳回人数是否达标，或通过人数是否已使驳回条件无法达成。
         *
         * @param context 票签统计上下文
         * @return 是否满足继续流转条件
         */
        @Override
        public boolean matches(Context context) {
            // 当前办理结果尚未写入历史列表，因此通过/驳回计数需要加上本次结果。
            String rejectCount = StringUtils.substring(context.nodeRatio
                , context.nodeRatio.indexOf("=") + 1);
            int count = Integer.parseInt(rejectCount);
            return (!context.isPass && context.doneRejectList.size() + 1 >= count)
                || (context.isPass && context.donePassList.size() + 1 > context.allNum - count);
        }
    }

    /** 按通过比例计算票签结果，并作为其他格式均未命中时的兜底规则。 */
    private static final class PassRatioRule implements Rule {

        /**
         * 比例规则作为兜底规则，始终支持当前配置。
         *
         * @param nodeRatio 节点票签规则配置
         * @return 固定返回 {@code true}
         */
        @Override
        public boolean supports(String nodeRatio) {
            return true;
        }

        /**
         * 将本次办理结果计入统计后计算通过和驳回比例。
         *
         * @param context 票签统计上下文
         * @return 是否满足继续流转条件
         */
        @Override
        public boolean matches(Context context) {
            // 比例规则同样把当前办理结果计入分子，再与节点配置比例比较。
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

        /**
         * 创建不可变统计快照，空列表和空变量统一转换为空集合。
         *
         * @param nodeRatio     节点票签规则配置
         * @param skipType     当前跳转类型
         * @param isPass       当前办理是否通过
         * @param allNum       总办理人数
         * @param todoList     尚未办理的人员列表
         * @param donePassList 已通过的历史记录
         * @param doneRejectList 已驳回的历史记录
         * @param variable     流程变量
         */
        Context(String nodeRatio, String skipType, boolean isPass, int allNum, List<?> todoList
            , List<?> donePassList, List<?> doneRejectList, Map<String, Object> variable) {
            this.nodeRatio = nodeRatio;
            this.skipType = skipType;
            this.isPass = isPass;
            this.allNum = allNum;
            this.todoList = defaultList(todoList);
            this.donePassList = defaultList(donePassList);
            this.doneRejectList = defaultList(doneRejectList);
            this.variable = variable == null ? Map.of() : variable;
        }

        /**
         * 将可能为空的统计列表统一为空集合，保持规则计算安全。
         *
         * @param list 原始统计列表
         * @return 原列表或空集合
         */
        private static List<?> defaultList(List<?> list) {
            return list == null ? List.of() : list;
        }
    }
}
