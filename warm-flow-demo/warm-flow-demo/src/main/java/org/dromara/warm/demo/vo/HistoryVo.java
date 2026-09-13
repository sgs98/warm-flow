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
     * 流程状态编码。
     */
    private String flowStatusKey;

    /**
     * 流程状态名称。
     */
    private String flowStatusName;

    /**
     * 业务状态编码。
     */
    private String businessStatus;

    /**
     * 业务状态名称。
     */
    private String businessStatusName;

    /**
     * 是否为当前活动待办。
     */
    private boolean current;

    /**
     * 任务操作状态名称。
     */
    private String taskStatusName;

    /**
     * 办理意见。
     */
    private String message;

    /**
     * 创建时间。
     */
    private Date createTime;
}
