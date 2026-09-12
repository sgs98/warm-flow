package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.utils.AssertUtil;

/**
 * 流程任务执行上下文加载器。
 *
 * @author may
 */
final class FlowTaskContextLoader {

    private FlowTaskContextLoader() {
    }

    /**
     * 加载任务执行需要的实例、定义和当前节点，并统一校验可执行状态。
     *
     * @param task 当前待办任务
     * @return 任务执行上下文
     */
    static TaskServiceImpl.R load(Task task) {
        AssertUtil.isNull(task, ExceptionCons.NOT_FOUNT_TASK);
        Instance instance = FlowEngine.insService().getById(task.getInstanceId());
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        Definition definition = FlowEngine.defService().getById(instance.getDefinitionId());
        AssertUtil.isNull(definition, ExceptionCons.NOT_FOUNT_DEF);
        AssertUtil.isFalse(ActivityStatus.isActivity(definition.getActivityStatus())
            && ActivityStatus.isActivity(instance.getActivityStatus()), ExceptionCons.NOT_ACTIVITY);
        AssertUtil.isTrue(FlowStatusMachine.isTerminal(instance.getFlowStatus()), ExceptionCons.FLOW_FINISH);
        AssertUtil.isTrue(NodeType.isEnd(instance.getNodeType()), ExceptionCons.FLOW_FINISH);
        Node nowNode = FlowEngine.nodeService().getByDefIdAndNodeCode(task.getDefinitionId(), task.getNodeCode());
        AssertUtil.isNull(nowNode, ExceptionCons.LOST_CUR_NODE);
        return new TaskServiceImpl.R(instance, definition, nowNode, task);
    }
}
