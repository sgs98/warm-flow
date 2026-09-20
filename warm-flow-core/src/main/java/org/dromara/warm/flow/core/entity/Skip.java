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

/**
 * 节点跳转关联对象 flow_skip
 *
 * @author warm
 * @since 2023-03-29
 */
public interface Skip extends RootEntity {

    @Override
    Long getId();

    @Override
    Skip setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    Skip setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    Skip setUpdateTime(Date updateTime);

    @Override
    String getCreateBy();

    @Override
    Skip setCreateBy(String createBy);

    @Override
    String getUpdateBy();

    @Override
    Skip setUpdateBy(String updateBy);

    @Override
    String getTenantId();

    @Override
    Skip setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    Skip setDelFlag(String delFlag);

    /**
     * 获取所属流程定义 ID。
     *
     * @return 流程定义 ID
     */
    Long getDefinitionId();

    /**
     * 设置所属流程定义 ID。
     *
     * @param definitionId 流程定义 ID
     * @return 当前连线
     */
    Skip setDefinitionId(Long definitionId);

    /**
     * 获取所属节点 ID。
     *
     * @return 节点 ID
     */
    Long getNodeId();

    /**
     * 设置所属节点 ID。
     *
     * @param nodeId 节点 ID
     * @return 当前连线
     */
    Skip setNodeId(Long nodeId);

    /**
     * 获取当前节点编码。
     *
     * @return 当前节点编码
     */
    String getNowNodeCode();

    /**
     * 设置当前节点编码。
     *
     * @param nowNodeCode 当前节点编码
     * @return 当前连线
     */
    Skip setNowNodeCode(String nowNodeCode);

    /**
     * 获取当前节点类型。
     *
     * @return 当前节点类型
     */
    Integer getNowNodeType();

    /**
     * 设置当前节点类型。
     *
     * @param nowNodeType 当前节点类型
     * @return 当前连线
     */
    Skip setNowNodeType(Integer nowNodeType);

    /**
     * 获取目标节点编码。
     *
     * @return 目标节点编码
     */
    String getNextNodeCode();

    /**
     * 设置目标节点编码。
     *
     * @param nextNodeCode 目标节点编码
     * @return 当前连线
     */
    Skip setNextNodeCode(String nextNodeCode);

    /**
     * 获取目标节点类型。
     *
     * @return 目标节点类型
     */
    Integer getNextNodeType();

    /**
     * 设置目标节点类型。
     *
     * @param nextNodeType 目标节点类型
     * @return 当前连线
     */
    Skip setNextNodeType(Integer nextNodeType);

    /**
     * 获取跳转线名称。
     *
     * @return 跳转线名称
     */
    String getSkipName();

    /**
     * 设置跳转线名称。
     *
     * @param skipName 跳转线名称
     * @return 当前连线
     */
    Skip setSkipName(String skipName);

    /**
     * 获取跳转类型。
     *
     * @return 跳转类型
     * @see org.dromara.warm.flow.core.enums.SkipType
     */
    String getSkipType();

    /**
     * 设置跳转类型。
     *
     * @param skipType 跳转类型
     * @return 当前连线
     */
    Skip setSkipType(String skipType);

    /**
     * 获取跳转条件表达式。
     *
     * @return 条件表达式
     */
    String getSkipCondition();

    /**
     * 设置跳转条件表达式。
     *
     * @param skipCondition 条件表达式
     * @return 当前连线
     */
    Skip setSkipCondition(String skipCondition);

    /**
     * 获取流程图连线坐标。
     *
     * @return 连线坐标
     */
    String getCoordinate();

    /**
     * 设置流程图连线坐标。
     *
     * @param coordinate 连线坐标
     * @return 当前连线
     */
    Skip setCoordinate(String coordinate);

    /**
     * 复制连线基础属性。
     * <p>
     * 不复制主键和所属节点 ID，主要用于流程定义复制或版本复制时重新生成连线。
     *
     * @return 新连线实体
     */
    default Skip copy() {
        return FlowEngine.newSkip()
            .setTenantId(getTenantId())
            .setDelFlag(getDelFlag())
            .setDefinitionId(getDefinitionId())
            .setNowNodeCode(getNowNodeCode())
            .setNowNodeType(getNowNodeType())
            .setNextNodeCode(getNextNodeCode())
            .setNextNodeType(getNextNodeType())
            .setSkipName(getSkipName())
            .setSkipType(getSkipType())
            .setSkipCondition(getSkipCondition())
            .setCoordinate(getCoordinate());
    }

}
