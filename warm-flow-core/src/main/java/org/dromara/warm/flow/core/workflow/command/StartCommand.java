package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 启动流程实例参数。只创建实例和首个待办，不办理首节点。
 *
 * @author may
 */
@Getter
@Setter
public class StartCommand extends WorkflowCommand {

    /**
     * 业务系统中的业务主键。
     */
    private String businessId;

    /**
     * 已发布流程定义的编码。
     */
    private String flowCode;

    /**
     * 本次操作需要写入流程实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 可选的流程实例状态。
     */
    private String flowStatus;

    /**
     * 指定首个待办的办理人。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到引擎计算出的办理人集合。
     */
    private boolean nextHandlerAppend;

}
