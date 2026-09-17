package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.List;

/**
 * 任务历史和实例写入处理器。
 *
 * <p>只负责一次任务流转中的持久化编排，不计算流程路径，也不执行监听器。</p>
 *
 * @author may
 */
final class TaskHistoryHandler {

    /**
     * 按既有顺序保存历史任务、删除当前待办、更新实例并创建后续待办和办理人。
     *
     * @param taskService 任务服务，用于复用既有任务持久化能力
     * @param task        当前待办任务
     * @param instance    流程实例
     * @param addTasks    待创建的后续任务
     * @param context     流程执行上下文
     * @param skipType    流转类型
     * @param nextNodes   目标节点
     */
    void updateFlowInfo(TaskServiceImpl taskService, Task task, Instance instance, List<Task> addTasks
        , WorkflowContext context, String skipType, List<Node> nextNodes) {
        HisTask insHis = FlowEngine.hisTaskService().setSkipInsHis(task, nextNodes, context, skipType);
        FlowEngine.hisTaskService().save(insHis);
        taskService.removeAndUserInternal(List.of(task));

        List<User> users = FlowEngine.userService().taskAddUsers(addTasks);
        taskService.setInsFinishInfo(instance, addTasks, context.getVariables());
        if (CollUtil.isNotEmpty(addTasks)) {
            taskService.saveBatch(addTasks);
        }
        boolean updated = FlowEngine.insService().updateById(instance);
        AssertUtil.isFalse(updated, ExceptionCons.NOT_FOUNT_INSTANCE);
        FlowEngine.userService().saveBatch(users);
    }
}
