package org.dromara.warm.demo.enums;

import lombok.Getter;
import org.dromara.warm.flow.core.enums.FlowStatus;

/**
 * BusinessStatusEnum
 *
 * @author may
 * @since 2026/9/6
 */
@Getter
public enum BusinessStatusEnum {
    CANCEL("cancel", "已撤销"), DRAFT("draft", "草稿"), WAITING("waiting", "待审核"),
    FINISH("finish", "已完成"), INVALID("invalid", "已作废"), BACK("back", "已退回"),
    TERMINATION("termination", "已终止");

    /**
     * -- GETTER --
     *  获取业务状态编码。
     */
    private final String status;
    /**
     * -- GETTER --
     *  获取业务状态描述。
     */
    private final String desc;

    BusinessStatusEnum(String status, String desc) {
        this.status = status;
        this.desc = desc;
    }

    /**
     * 将 Warm-Flow 内部状态映射为业务状态。
     */
    public static BusinessStatusEnum fromFlowStatus(String flowStatus) {
        if (FlowStatus.CANCEL.getKey().equals(flowStatus)) return CANCEL;
        if (FlowStatus.REJECT.getKey().equals(flowStatus)) return BACK;
        if (FlowStatus.TERMINATE.getKey().equals(flowStatus)) return TERMINATION;
        if (FlowStatus.NULLIFY.getKey().equals(flowStatus) || FlowStatus.INVALID.getKey().equals(flowStatus))
            return INVALID;
        if (FlowStatus.PASS.getKey().equals(flowStatus) || FlowStatus.AUTO_PASS.getKey().equals(flowStatus)
            || FlowStatus.FINISHED.getKey().equals(flowStatus)) return FINISH;
        if (FlowStatus.TOBESUBMIT.getKey().equals(flowStatus) || FlowStatus.PENDING.getKey().equals(flowStatus))
            return DRAFT;
        return WAITING;
    }
}
