package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.dto.FlowParams;
import org.dromara.warm.flow.core.enums.FlowStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 流程执行内部状态机。
 *
 * <p>集中流程状态默认值和终态判断；业务方自定义状态继续按原有方式透传。</p>
 *
 * @author may
 */
final class FlowStatusMachine {

    /**
     * 不允许继续执行流程操作的标准终态。
     */
    private static final Set<String> TERMINAL_STATUS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        FlowStatus.FINISHED.getKey(), FlowStatus.TERMINATE.getKey(), FlowStatus.NULLIFY.getKey(),
        FlowStatus.CANCEL.getKey(), FlowStatus.INVALID.getKey())));

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
     * 获取历史任务使用的自定义状态，历史状态优先于流程状态。
     *
     * @param flowParams 流程操作参数
     * @return 自定义状态，未设置时返回空值
     */
    static String customStatus(FlowParams flowParams) {
        return StringUtils.emptyDefault(flowParams.getHisStatus(), flowParams.getFlowStatus());
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
