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
package org.dromara.warm.flow.core.entity;

import java.util.Date;
import java.util.List;

/**
 * 待办任务记录对象 flow_task
 *
 * @author warm
 * @since 2023-03-29
 */
public interface Task extends RootEntity {

    @Override
    Long getId();

    @Override
    Task setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    Task setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    Task setUpdateTime(Date updateTime);

    @Override
    String getCreateBy();

    @Override
    Task setCreateBy(String createBy);

    @Override
    String getUpdateBy();

    @Override
    Task setUpdateBy(String updateBy);

    @Override
    String getTenantId();

    @Override
    Task setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    Task setDelFlag(String delFlag);

    /**
     * 获取流程定义ID
     *
     * @return 流程定义ID
     */
    Long getDefinitionId();

    Task setDefinitionId(Long definitionId);

    /**
     * 获取流程实例ID
     *
     * @return 流程实例ID
     */
    Long getInstanceId();

    Task setInstanceId(Long instanceId);

    String getFlowName();

    /**
     * 设置流程名称。
     *
     * @param flowName 流程名称
     * @return 当前待办任务
     */
    Task setFlowName(String flowName);

    /**
     * 获取业务ID
     *
     * @return 业务ID
     */
    String getBusinessId();

    Task setBusinessId(String businessId);

    /**
     * 获取当前任务节点编码。
     *
     * @return 节点编码
     */
    String getNodeCode();

    /**
     * 设置当前任务节点编码。
     *
     * @param nodeCode 节点编码
     * @return 当前待办任务
     */
    Task setNodeCode(String nodeCode);

    /**
     * 获取当前任务节点名称。
     *
     * @return 节点名称
     */
    String getNodeName();

    /**
     * 设置当前任务节点名称。
     *
     * @param nodeName 节点名称
     * @return 当前待办任务
     */
    Task setNodeName(String nodeName);

    /**
     * 获取当前任务节点类型。
     *
     * @return 节点类型
     * @see org.dromara.warm.flow.core.enums.NodeType
     */
    Integer getNodeType();

    /**
     * 设置当前任务节点类型。
     *
     * @param nodeType 节点类型
     * @return 当前待办任务
     */
    Task setNodeType(Integer nodeType);

    /**
     * 获取流程状态
     *
     * @return 流程状态
     * @see org.dromara.warm.flow.core.enums.FlowStatus
     */
    String getFlowStatus();

    Task setFlowStatus(String flowStatus);

    /**
     * 获取可办理该任务的权限标识列表。
     *
     * @return 权限标识列表
     */
    List<String> getPermissionList();

    /**
     * 设置可办理该任务的权限标识列表。
     *
     * @param permissionList 权限标识列表
     * @return 当前待办任务
     */
    Task setPermissionList(List<String> permissionList);

    /**
     * 获取任务关联的办理人记录。
     *
     * @return 办理人记录列表
     */
    List<User> getUserList();

    /**
     * 设置任务关联的办理人记录。
     *
     * @param userList 办理人记录列表
     * @return 当前待办任务
     */
    Task setUserList(List<User> userList);

    /**
     * 获取任务表单自定义标识。
     *
     * @return 表单自定义标识
     */
    String getFormCustom();

    /**
     * 设置任务表单自定义标识。
     *
     * @param formCustom 表单自定义标识
     * @return 当前待办任务
     */
    Task setFormCustom(String formCustom);

    /**
     * 获取任务表单路径或标识。
     *
     * @return 表单路径或标识
     */
    String getFormPath();

    /**
     * 设置任务表单路径或标识。
     *
     * @param formPath 表单路径或标识
     * @return 当前待办任务
     */
    Task setFormPath(String formPath);
}
