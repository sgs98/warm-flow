package org.dromara.warm.flow.core.workflow.command;

import lombok.Getter;
import lombok.Setter;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

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
     * 启动流程时写入实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 可选的流程实例初始状态，未设置时使用引擎默认状态。
     */
    private String instanceStatus;

    /**
     * 可选的开始节点历史任务状态，未设置时使用引擎默认状态。
     */
    private String historyTaskStatus;

    /**
     * 指定首个待办节点的办理人集合。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到引擎计算出的办理人集合。
     */
    private boolean nextHandlerAppend;

    /**
     * 将启动参数填充到执行上下文。
     *
     * @param context 流程执行上下文
     */
    @Override
    public void fillContext(WorkflowContext context) {
        context.setVariables(getVariables());
        context.setInstanceStatus(getInstanceStatus());
        context.setHistoryTaskStatus(getHistoryTaskStatus());
        context.setNextHandlers(getNextHandlers());
        context.setNextHandlerAppend(isNextHandlerAppend());
    }
}
