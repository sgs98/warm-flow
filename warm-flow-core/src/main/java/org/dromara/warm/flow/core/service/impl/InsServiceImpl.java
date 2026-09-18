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
import org.dromara.warm.flow.core.entity.*;
import org.dromara.warm.flow.core.enums.ActivityStatus;
import org.dromara.warm.flow.core.orm.dao.FlowInstanceDao;
import org.dromara.warm.flow.core.orm.service.impl.WarmServiceImpl;
import org.dromara.warm.flow.core.service.InsService;
import org.dromara.warm.flow.core.utils.*;
import org.dromara.warm.flow.core.workflow.context.WorkflowContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程实例Service业务层处理
 *
 * @author warm
 * @since 2023-03-29
 */
public class InsServiceImpl extends WarmServiceImpl<FlowInstanceDao<Instance>, Instance> implements InsService {

    @Override
    public InsService setDao(FlowInstanceDao<Instance> warmDao) {
        this.warmDao = warmDao;
        return this;
    }

    @Override
    public Instance start(String businessId, String flowCode, WorkflowContext context) {
        AssertUtil.isNull(flowCode, ExceptionCons.NULL_FLOW_CODE);
        AssertUtil.isEmpty(businessId, ExceptionCons.NULL_BUSINESS_ID);
        FlowExecution execution = FlowExecution.loadStart(flowCode, context);
        return new FlowStartChain(this, businessId).pipeline().run(execution);
    }

    @Override
    public List<Instance> listByDefIds(List<Long> defIds) {
        return getDao().getByDefIds(defIds);
    }

    @Override
    public boolean remove(List<Long> instanceIds) {
        return toRemoveTask(instanceIds);
    }

    @Override
    public List<Instance> getByDefId(Long definitionId) {
        return list(FlowEngine.newIns().setDefinitionId(definitionId));
    }

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

    @Override
    public boolean active(Long id) {
        Instance instance = getById(id);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        AssertUtil.isTrue(ActivityStatus.isActivity(instance.getActivityStatus()), ExceptionCons.INSTANCE_ALREADY_ACTIVITY);
        instance.setActivityStatus(ActivityStatus.ACTIVITY.getKey());
        return updateById(instance);
    }

    @Override
    public boolean unActive(Long id) {
        Instance instance = getById(id);
        AssertUtil.isNull(instance, ExceptionCons.NOT_FOUNT_INSTANCE);
        AssertUtil.isTrue(ActivityStatus.isSuspended(instance.getActivityStatus()), ExceptionCons.INSTANCE_ALREADY_SUSPENDED);
        instance.setActivityStatus(ActivityStatus.SUSPENDED.getKey());
        return updateById(instance);
    }

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
