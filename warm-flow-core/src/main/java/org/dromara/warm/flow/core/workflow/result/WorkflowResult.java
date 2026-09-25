package org.dromara.warm.flow.core.workflow.result;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 流程写操作统一返回结果。
 *
 * @author may
 */
@Getter
@Setter
public class WorkflowResult {

    /**
     * 本次操作是否成功。
     */
    private boolean success;

    /**
     * 操作名称。
     */
    private String operation;

    /**
     * 流程实例主键。
     */
    private Long instanceId;

    /**
     * 业务系统中的业务主键。
     */
    private String businessId;

    /**
     * 操作完成后的流程实例状态。
     */
    private String flowStatus;

    /**
     * 本次完成或协作操作关联的任务主键。
     */
    private Long completedTaskId;

    /**
     * 操作完成后仍处于待办状态的任务。
     */
    private List<WorkflowTaskView> currentTasks;
}
