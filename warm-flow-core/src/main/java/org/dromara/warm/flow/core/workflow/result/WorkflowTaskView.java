package org.dromara.warm.flow.core.workflow.result;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 对外返回的待办任务视图，不直接暴露 ORM 实体。
 *
 * @author may
 */
@Getter
@Setter
public class WorkflowTaskView {

    /**
     * 待办任务主键。
     */
    private Long taskId;

    /**
     * 所属流程实例主键。
     */
    private Long instanceId;

    /**
     * 节点编码。
     */
    private String nodeCode;

    /**
     * 节点名称。
     */
    private String nodeName;

    /**
     * 节点类型。
     */
    private Integer nodeType;

    /**
     * 待办任务状态。
     */
    private String taskStatus;

    /**
     * 当前任务办理人标识集合。
     */
    private List<String> handlers;
}
