/*
 *    Copyright 2024-2025, Warm-Flow (290631660@qq.com).
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.dromara.warm.flow.core.service.impl;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.constant.ExceptionCons;
import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.entity.Task;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.orm.dao.FlowInstanceDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.utils.AssertUtil;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程实例服务实现。
 *
 * <p>负责启动流程、实例启停、级联清理实例运行数据，以及维护实例变量。</p>
 *
 * @author warm
 * @since 2023-03-29
 */
public class InsServiceImpl extends WarmServiceImpl<FlowInstanceDao<Instance>, Instance> implements InsService {

    /**
     * 注入流程实例 DAO。
     *
     * @param warmDao 流程实例数据访问对象
     * @return 当前服务实例
     */
    @Override
    public InsService setDao(FlowInstanceDao<Instance> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    /**
     * 按业务标识和流程编码启动流程实例。
     *
     * @param businessId 业务主键
     * @param flowCode   流程编码
     * @param context    启动上下文
     * @return 已持久化的流程实例
     */
    @Override
    public Instance start(String businessId, String flowCode, WorkflowContext context) {
        AssertUtil.isNull(flowCode, ExceptionCons.NULL_FLOW_CODE);
        AssertUtil.isEmpty(businessId, ExceptionCons.NULL_BUSINESS_ID);
        FlowExecution execution = FlowExecution.loadStart(flowCode, context);
        return new FlowStartChain(this, businessId).pipeline().run(execution);
    }

    /**
     * 按流程定义主键集合查询实例。
     *
     * @param defIds 流程定义主键集合
     * @return 命中的流程实例
     */
    @Override
    public List<Instance> listByDefIds(List<Long> defIds) {
        return getDao().getByDefIds(defIds);
    }

    /**
     * 删除流程实例及其待办、历史任务和办理人数据。
     *
     * @param instanceIds 流程实例主键集合
     * @return 是否删除成功
     */
    @Override
    public boolean remove(List<Long> instanceIds) {
        return toRemoveTask(instanceIds);
    }

    /**
     * 按流程定义主键查询实例。
     *
     * @param definitionId 流程定义主键
     * @return 该定义下的流程实例
     */
    @Override
    public List<Instance> getByDefId(Long definitionId) {
        return list(FlowEngine.newIns().setDefinitionId(definitionId));
    }

    /**
     * 按办理人、待办、历史任务、实例的依赖顺序级联清理运行数据。
     *
     * @param instanceIds 流程实例主键集合
     * @return 是否删除成功
     */
    private boolean toRemoveTask(List<Long> instanceIds) {
        AssertUtil.isEmpty(instanceIds, ExceptionCons.NULL_INSTANCE_ID);

        List<Long> taskIds = new ArrayList<>();
        instanceIds.forEach(instanceId -> taskIds.addAll(
            FlowEngine.taskService()
                .list(FlowEngine.newTask().setInstanceId(instanceId))
                .stream()
                .map(Task::getId)
                .toList()));

        if (CollUtil.isNotEmpty(taskIds)) {
            FlowEngine.userService().deleteByTaskIds(taskIds);
        }

        FlowEngine.taskService().deleteByInsIds(instanceIds);
        FlowEngine.hisTaskService().deleteByInsIds(instanceIds);
        return FlowEngine.insService().removeByIds(instanceIds);
    }

    /**
     * 激活已挂起的流程实例。
     *
     * @param id 流程实例主键
     * @return 是否更新成功
     */
    @Override
    public boolean active(Long id) {
        Instance instance = getById(id);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        AssertUtil.isTrue(ActivityStatus.isActivity(instance.getActivityStatus()), ExceptionCons.INSTANCE_ALREADY_ACTIVITY);
        instance.setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        return updateById(instance);
    }

    /**
     * 挂起活动中的流程实例。
     *
     * @param id 流程实例主键
     * @return 是否更新成功
     */
    @Override
    public boolean unActive(Long id) {
        Instance instance = getById(id);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        AssertUtil.isTrue(ActivityStatus.isSuspended(instance.getActivityStatus()), ExceptionCons.INSTANCE_ALREADY_SUSPENDED);
        instance.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        return updateById(instance);
    }

    /**
     * 从实例变量中移除指定键；实例不存在时不执行更新。
     *
     * @param instanceId 流程实例主键
     * @param keys       待移除的变量键
     */
    @Override
    public void removeVariables(Long instanceId, String... keys) {
        Instance instance = FlowEngine.insService().getById(instanceId);
        if (instance != null) {
            Map<String, Object> variableMap = instance.getVariableMap();
            for (String key : keys) {
                variableMap.remove(key);
            }
            instance.setVariable(FlowEngine.jsonConvert.objToStr(variableMap));
            FlowEngine.insService().updateById(instance);
        }
    }
}
