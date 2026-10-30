package org.dromara.warm.flow.core.workflow.context;

import lombok.Getter;
import lombok.Setter;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.utils.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程动作的内部执行上下文。
 *
 * <p>该对象承载流程执行期间需要传递和演进的数据。标准业务操作优先使用各自的
 * Command，由引擎映射为本上下文；引擎扩展场景也可以按需直接创建或传递本对象。</p>
 *
 * @author may
 */
@Getter
@Setter
public class WorkflowContext {

    /**
     * 当前操作者唯一标识。
     */
    private String handler;

    /**
     * 调用方显式指定的租户标识（消息触发等场景优先于 TenantHandler 与系统兜底）。
     */
    private String tenantId;

    /**
     * 当前操作者可用于权限匹配的标识集合。
     */
    private List<String> permissions;

    /**
     * 是否忽略流程办理权限校验。
     */
    private boolean ignorePermission;

    /**
     * 是否忽略权限校验、受托人委派处理和会签/票签协作规则（true：单方办理即可推动节点流转）。
     */
    private boolean ignore;

    /**
     * 本次流程动作的说明。
     */
    private String message;

    /**
     * 本次流程动作需要写入实例的变量。
     */
    private Map<String, Object> variables;

    /**
     * 调用方指定的流程实例状态，未设置时由引擎状态机决定。
     */
    private String flowStatus;

    /**
     * 调用方指定的历史任务状态，未设置时由引擎状态机决定。
     */
    private String taskStatus;

    /**
     * 流程动作的目标节点编码。
     */
    private String targetNodeCode;

    /**
     * 指定的后续节点办理人集合。
     */
    private List<String> nextHandlers;

    /**
     * 是否将指定办理人追加到引擎计算出的办理人集合。
     */
    private boolean nextHandlerAppend;

    /**
     * 调用方传入的扩展信息。
     */
    private String ext;

    /**
     * 使用配置中的系统操作人创建流程上下文
     *
     * @return 系统操作人流程上下文
     */
    public static WorkflowContext create() {
        return create(null);
    }

    /**
     * 使用指定操作人创建流程上下文
     *
     * @param handler 操作人标识
     * @return 初始化操作人的流程上下文
     */
    public static WorkflowContext create(String handler) {
        WorkflowContext context = new WorkflowContext();
        if (StringUtils.isEmpty(handler)) {
            context.setHandler(FlowEngine.getFlowConfig().getSystemHandler());
        } else {
            context.setHandler(handler);
        }
        return context;
    }

    /**
     * 构造自动节点解析后继用的上下文：忽略调用方指定的目标节点与后续办理人，仅携带最新变量。
     *
     * @param variables 最新流程变量
     * @return 解析后继用的流程上下文
     */
    public static WorkflowContext buildAutoSuccessorContext(Map<String, Object> variables) {
        WorkflowContext context = new WorkflowContext();
        Map<String, Object> safeVars = variables == null ? Collections.emptyMap() : new HashMap<>(variables);
        context.setVariables(safeVars);
        return context;
    }

}
