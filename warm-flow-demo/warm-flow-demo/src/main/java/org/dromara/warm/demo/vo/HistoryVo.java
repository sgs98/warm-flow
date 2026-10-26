package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 流程历史记录 VO。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class HistoryVo {

    /**
     * 历史任务主键。
     */
    private Long id;

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 流程名称。
     */
    private String flowName;

    /**
     * 当前节点编码。
     */
    private String nodeCode;

    /**
     * 当前节点名称。
     */
    private String nodeName;

    /**
     * 目标节点编码。
     */
    private String targetNodeCode;

    /**
     * 目标节点名称。
     */
    private String targetNodeName;

    /**
     * 办理人。
     */
    private String approver;

    /**
     * 跳转类型。
     */
    private String skipType;

    /**
     * 任务状态编码（pending / pass / back / invalid / copy 等）。
     */
    private String taskStatus;

    /**
     * 任务状态名称。
     */
    private String taskStatusName;

    /**
     * 是否为当前活动待办。
     */
    private boolean current;

    /**
     * 办理意见。
     */
    private String message;

    /**
     * 抄送人（仅抄送记录有值，逗号分隔）。
     */
    private String copyUsers;

    /**
     * 创建时间。
     */
    private Date createTime;
}
