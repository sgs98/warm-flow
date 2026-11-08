package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;

/**
 * 结束流程实例重置兼容入口。
 *
 * @author warm
 * @deprecated 请使用 {@link InstanceRestartCoordinator}，流程恢复语义统一为“重启”。
 */
@Deprecated
public final class InstanceResetCoordinator {

    private InstanceResetCoordinator() {
    }

    /**
     * 将终态实例重启到指定人工审批节点。
     *
     * @param instanceId     流程实例主键
     * @param targetNodeCode 目标节点编码
     * @param handlers       指定审批人
     * @param context        操作上下文
     * @return 重启后的流程实例
     * @deprecated 请使用 {@link InstanceRestartCoordinator#restart(Long, String, List, WorkflowContext)}。
     */
    @Deprecated
    public static Instance reset(Long instanceId, String targetNodeCode, List<String> handlers
        , WorkflowContext context) {
        return InstanceRestartCoordinator.restart(instanceId, targetNodeCode, handlers, context);
    }
}
