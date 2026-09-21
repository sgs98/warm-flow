package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.*;

/**
 * 流程执行内部状态机。
 *
 * <p>集中流程状态默认值、终态判断与操作状态前置（guard 表）；业务方自定义状态继续按原有方式透传。</p>
 *
 * @author may
 */
final class FlowStatusMachine {

    /**
     * 不允许继续执行流程操作的标准终态。
     */
    private static final Set<String> TERMINAL_STATUS = Set.of(
        FlowStatus.FINISHED.getKey(), FlowStatus.TERMINATE.getKey(), FlowStatus.NULLIFY.getKey(),
        FlowStatus.CANCEL.getKey(), FlowStatus.INVALID.getKey());

    /**
     * 状态谓词 guard。声明顺序即断言顺序（EnumSet 按自然序迭代），不得重排。
     */
    private enum StateGuard {

        /**
         * 定义与实例均处于激活状态。
         */
        ACTIVITY,

        /**
         * 实例流程状态非终态。
         */
        NOT_TERMINAL,

        /**
         * 实例当前节点非结束节点。
         */
        NOT_END
    }

    /**
     * 办理族通用行：激活 ∧ 非终态 ∧ 非结束节点（五个操作共用，只读）。
     */
    private static final EnumSet<StateGuard> OPERABLE_GUARDS =
        EnumSet.of(StateGuard.ACTIVITY, StateGuard.NOT_TERMINAL, StateGuard.NOT_END);

    /**
     * 操作 × 状态谓词 guard 表：每个操作声明其放行需要满足的状态谓词集合，按声明序逐一断言。
     */
    private static final Map<FlowOp, EnumSet<StateGuard>> GUARDS;

    static {
        Map<FlowOp, EnumSet<StateGuard>> table = new EnumMap<>(FlowOp.class);
        table.put(FlowOp.EXECUTE, OPERABLE_GUARDS);
        table.put(FlowOp.TERMINATE, OPERABLE_GUARDS);
        table.put(FlowOp.UPDATE_HANDLERS, OPERABLE_GUARDS);
        table.put(FlowOp.LOAD, OPERABLE_GUARDS);
        table.put(FlowOp.REVOKE, OPERABLE_GUARDS);
        // 删除行（唯一离群行）：仅要求激活——已终态/已结束实例允许删除清理
        table.put(FlowOp.DELETE, EnumSet.of(StateGuard.ACTIVITY));
        GUARDS = Collections.unmodifiableMap(table);
    }

    /**
     * 工具类不允许实例化。
     */
    private FlowStatusMachine() {
    }

    /**
     * 判断流程状态是否为标准终态。
     *
     * @param status 流程状态
     * @return 是否为终态
     */
    static boolean isTerminal(String status) {
        return TERMINAL_STATUS.contains(status);
    }

    /**
     * 按 guard 表校验操作的状态前置，谓词违例即抛出对应异常。
     *
     * @param op         流程操作
     * @param definition 流程定义
     * @param instance   流程实例
     */
    static void checkGuards(FlowOp op, Definition definition, Instance instance) {
        EnumSet<StateGuard> guards = Objects.requireNonNull(GUARDS.get(op),
            () -> "FlowOp 未配置状态前置: " + op);
        for (StateGuard guard : guards) {
            apply(guard, definition, instance);
        }
    }

    /**
     * 应用单个状态前置并抛出与既有流程操作一致的业务异常。
     *
     * @param guard       状态前置
     * @param definition  流程定义
     * @param instance    流程实例
     */
    private static void apply(StateGuard guard, Definition definition, Instance instance) {
        switch (guard) {
            case ACTIVITY -> AssertUtil.isFalse(ActivityStatus.isActivity(definition.getActivityStatus())
                && ActivityStatus.isActivity(instance.getActivityStatus()), ExceptionCons.NOT_ACTIVITY);
            case NOT_TERMINAL -> AssertUtil.isTrue(isTerminal(instance.getFlowStatus()), ExceptionCons.FLOW_FINISH);
            case NOT_END -> AssertUtil.isTrue(NodeType.isEnd(instance.getNodeType()), ExceptionCons.FLOW_FINISH);
            default -> throw new IllegalStateException("未知状态前置: " + guard);
        }
    }

    /**
     * 获取历史任务使用的自定义状态，历史状态优先于流程状态。
     *
     * @param historyTaskStatus 调用方指定的历史任务状态
     * @param instanceStatus    调用方指定的流程实例状态
     * @return 自定义状态，未设置时返回空值
     */
    static String customStatus(String historyTaskStatus, String instanceStatus) {
        return StringUtils.emptyDefault(historyTaskStatus, instanceStatus);
    }

    /**
     * 根据目标节点和跳转类型计算新待办任务的默认状态。
     *
     * @param nodeType 目标节点类型
     * @param skipType 跳转类型
     * @return 待办任务默认状态
     */
    static String taskStatus(Integer nodeType, String skipType) {
        if (NodeType.isStart(nodeType)) {
            return FlowStatus.TOBESUBMIT.getKey();
        } else if (NodeType.isEnd(nodeType)) {
            return FlowStatus.FINISHED.getKey();
        } else if (SkipType.isReject(skipType)) {
            return FlowStatus.REJECT.getKey();
        }
        return FlowStatus.APPROVAL.getKey();
    }

    /**
     * 获取历史任务的跳转状态，自定义状态优先。
     *
     * @param customStatus 自定义状态
     * @param skipType     跳转类型
     * @return 历史任务状态
     */
    static String skipStatus(String customStatus, String skipType) {
        if (StringUtils.isNotEmpty(customStatus)) {
            return customStatus;
        }
        return SkipType.isReject(skipType) ? FlowStatus.REJECT.getKey() : FlowStatus.PASS.getKey();
    }

    /**
     * 优先使用自定义状态，否则返回操作默认状态。
     *
     * @param customStatus  自定义状态
     * @param defaultStatus 默认状态
     * @return 最终状态
     */
    static String defaultStatus(String customStatus, String defaultStatus) {
        return StringUtils.emptyDefault(customStatus, defaultStatus);
    }

}
