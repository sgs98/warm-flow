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
import java.util.List;

/**
 * 流程节点对象 flow_node
 *
 * @author warm
 * @since 2023-03-29
 */
public interface Node extends RootEntity {

    @Override
    Long getId();

    @Override
    Node setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    Node setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    Node setUpdateTime(Date updateTime);

    @Override
    String getCreateBy();

    @Override
    Node setCreateBy(String createBy);

    @Override
    String getUpdateBy();

    @Override
    Node setUpdateBy(String updateBy);

    @Override
    String getTenantId();

    @Override
    Node setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    Node setDelFlag(String delFlag);

    /**
     * 获取节点类型。
     *
     * @return 节点类型
     * @see org.dromara.warm.flow.core.enums.NodeType
     */
    Integer getNodeType();

    /**
     * 设置节点类型。
     *
     * @param nodeType 节点类型
     * @return 当前节点
     */
    Node setNodeType(Integer nodeType);

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
     * @return 当前节点
     */
    Node setDefinitionId(Long definitionId);

    /**
     * 获取节点编码。
     *
     * @return 节点编码
     */
    String getNodeCode();

    /**
     * 设置节点编码。
     *
     * @param nodeCode 节点编码
     * @return 当前节点
     */
    Node setNodeCode(String nodeCode);

    /**
     * 获取节点名称。
     *
     * @return 节点名称
     */
    String getNodeName();

    /**
     * 设置节点名称。
     *
     * @param nodeName 节点名称
     * @return 当前节点
     */
    Node setNodeName(String nodeName);

    /**
     * 获取协作比例或票签规则。
     *
     * @return 节点协作比例或规则
     */
    String getNodeRatio();

    /**
     * 设置协作比例或票签规则。
     *
     * @param nodeRatio 节点协作比例或规则
     * @return 当前节点
     */
    Node setNodeRatio(String nodeRatio);

    /**
     * 获取办理人权限表达式。
     *
     * @return 权限表达式
     */
    String getPermissionFlag();

    /**
     * 设置办理人权限表达式。
     *
     * @param permissionFlag 权限表达式
     * @return 当前节点
     */
    Node setPermissionFlag(String permissionFlag);

    /**
     * 获取流程图节点坐标。
     *
     * @return 节点坐标
     */
    String getCoordinate();

    /**
     * 设置流程图节点坐标。
     *
     * @param coordinate 节点坐标
     * @return 当前节点
     */
    Node setCoordinate(String coordinate);

    /**
     * 获取任意节点跳转配置。
     *
     * @return 任意节点跳转配置
     */
    String getAnyNodeSkip();

    /**
     * 设置任意节点跳转配置。
     *
     * @param anyNodeSkip 任意节点跳转配置
     * @return 当前节点
     */
    Node setAnyNodeSkip(String anyNodeSkip);

    /**
     * 获取节点监听器类型列表。
     *
     * @return 监听器类型
     */
    String getListenerType();

    /**
     * 设置节点监听器类型列表。
     *
     * @param listenerType 监听器类型
     * @return 当前节点
     */
    Node setListenerType(String listenerType);

    /**
     * 获取节点监听器路径列表。
     *
     * @return 监听器路径
     */
    String getListenerPath();

    /**
     * 设置节点监听器路径列表。
     *
     * @param listenerPath 监听器路径
     * @return 当前节点
     */
    Node setListenerPath(String listenerPath);

    /**
     * 获取节点表单自定义标识。
     *
     * @return 表单自定义标识
     */
    String getFormCustom();

    /**
     * 设置节点表单自定义标识。
     *
     * @param formCustom 表单自定义标识
     * @return 当前节点
     */
    Node setFormCustom(String formCustom);

    /**
     * 获取节点表单路径或标识。
     *
     * @return 表单路径或标识
     */
    String getFormPath();

    /**
     * 设置节点表单路径或标识。
     *
     * @param formPath 表单路径或标识
     * @return 当前节点
     */
    Node setFormPath(String formPath);

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
     * @return 当前节点
     */
    Node setExt(String ext);

    /**
     * 获取当前节点的出口连线列表。
     *
     * @return 出口连线列表
     */
    List<Skip> getSkipList();

    /**
     * 设置当前节点的出口连线列表。
     *
     * @param skipList 出口连线列表
     * @return 当前节点
     */
    Node setSkipList(List<Skip> skipList);

    /**
     * 复制节点基础属性。
     * <p>
     * 不复制主键和出口连线，主要用于流程定义复制或版本复制时重新生成节点。
     *
     * @return 新节点实体
     */
    default Node copy() {
        return FlowEngine.newNode()
            .setTenantId(this.getTenantId())
            .setDelFlag(this.getDelFlag())
            .setNodeType(this.getNodeType())
            .setDefinitionId(this.getDefinitionId())
            .setNodeCode(this.getNodeCode())
            .setNodeName(this.getNodeName())
            .setNodeRatio(this.getNodeRatio())
            .setPermissionFlag(this.getPermissionFlag())
            .setCoordinate(this.getCoordinate())
            .setAnyNodeSkip(this.getAnyNodeSkip())
            .setListenerType(this.getListenerType())
            .setListenerPath(this.getListenerPath())
            .setFormCustom(this.getFormCustom())
            .setFormPath(this.getFormPath())
            .setExt(this.getExt());
    }
}
