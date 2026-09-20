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

import org.dromara.warm.flow.core.FlowEngine;

import java.util.Date;
import java.util.Map;

/**
 * 流程实例对象 flow_instance
 *
 * @author warm
 * @since 2023-03-29
 */
public interface Instance extends RootEntity {

    @Override
    Long getId();

    @Override
    Instance setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    Instance setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    Instance setUpdateTime(Date updateTime);

    @Override
    String getCreateBy();

    @Override
    Instance setCreateBy(String createBy);

    @Override
    String getUpdateBy();

    @Override
    Instance setUpdateBy(String updateBy);

    @Override
    String getTenantId();

    @Override
    Instance setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    Instance setDelFlag(String delFlag);

    /**
     * flow_definition.id
     *
     * @return flow_definition.id
     */
    Long getDefinitionId();

    Instance setDefinitionId(Long definitionId);

    /**
     * 流程名称
     *
     * @return 流程名称
     */
    String getFlowName();

    Instance setFlowName(String flowName);

    /**
     * 业务ID
     *
     * @return 业务ID
     */
    String getBusinessId();

    Instance setBusinessId(String businessId);

    /**
     * @return 节点类型
     * @see org.dromara.warm.flow.core.enums.NodeType
     */
    Integer getNodeType();

    /**
     * 设置当前节点类型。
     *
     * @param nodeType 节点类型
     * @return 当前实例
     */
    Instance setNodeType(Integer nodeType);

    /**
     * 获取当前节点编码。
     *
     * @return 节点编码
     */
    String getNodeCode();

    /**
     * 设置当前节点编码。
     *
     * @param nodeCode 节点编码
     * @return 当前实例
     */
    Instance setNodeCode(String nodeCode);

    /**
     * 流程节点名称
     *
     * @return 节点名称
     */
    String getNodeName();

    Instance setNodeName(String nodeName);

    /**
     * 流程变量
     *
     * @return 流程变量
     */
    String getVariable();

    Instance setVariable(String variable);

    /**
     * 将流程变量 JSON 字符串转换为 Map。
     *
     * @return 流程变量 Map
     */
    default Map<String, Object> getVariableMap() {
        return FlowEngine.jsonConvert.strToMap(getVariable());
    }

    /**
     * @return 流程状态
     * @see org.dromara.warm.flow.core.enums.FlowStatus
     */
    String getFlowStatus();

    Instance setFlowStatus(String flowStatus);

    /**
     * 审批表单是否自定义（Y是 N否）
     *
     * @return （Y是 N否）
     */
    String getFormCustom();

    /**
     * 设置是否使用自定义表单。
     *
     * @param formCustom 是否自定义表单
     * @return 当前实例
     */
    Instance setFormCustom(String formCustom);

    /**
     * 获取表单路径或标识。
     *
     * @return 表单路径或标识
     */
    String getFormPath();

    /**
     * 设置表单路径或标识。
     *
     * @param formPath 表单路径或标识
     * @return 当前实例
     */
    Instance setFormPath(String formPath);

    /**
     * 获取启动时固化的流程定义 JSON。
     *
     * @return 流程定义 JSON
     */
    String getDefJson();

    /**
     * 设置启动时固化的流程定义 JSON。
     *
     * @param defJson 流程定义 JSON
     * @return 当前实例
     */
    Instance setDefJson(String defJson);

    /**
     * 获取扩展字段。
     *
     * @return 扩展字段
     */
    String getExt();

    /**
     * 设置扩展字段。
     *
     * @param ext 扩展字段
     * @return 当前实例
     */
    Instance setExt(String ext);

    /**
     * @return 激活状态
     * @see org.dromara.warm.flow.core.enums.ActivityStatus
     */
    Integer getActivityStatus();

    /**
     * 设置激活状态。
     *
     * @param activityStatus 激活状态
     * @return 当前实例
     */
    Instance setActivityStatus(Integer activityStatus);

}
