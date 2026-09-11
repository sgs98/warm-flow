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
     * 当前办理人集合。
     */
    private List<String> assignees;

    /**
     * 创建时间。
     */
    private Date createTime;
}
