package org.dromara.warm.demo.vo;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

/**
 * 待办/已办任务 VO。
 *
 * @author may
 * @since 2026/9/5
 */
@Getter
@Setter
public class TaskVo {

    /**
     * 任务主键。
     */
    private Long id;

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 流程定义主键。
     */
    private Long definitionId;

    /**
     * 流程名称。
     */
    private String flowName;

    /**
     * 业务主键。
     */
    private String businessId;

    /**
     * 当前节点编码。
     */
    private String nodeCode;

    /**
     * 当前节点名称。
     */
    private String nodeName;

    /**
     * 当前节点类型。
     */
    private Integer nodeType;

    /**
     * 任务状态编码（pending 待审批 / pass 通过 / back 退回 / copy 抄送等）。
     */
    private String taskStatus;

    /**
     * 任务状态名称；抄送列表按页面语义展示所属流程实例状态。
     */
    private String taskStatusName;

    /**
     * 当前办理人集合。
     */
    private List<String> assignees;

    /**
     * 创建时间。
     */
    private Date createTime;
}
