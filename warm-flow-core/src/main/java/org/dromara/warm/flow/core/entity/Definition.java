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
 * 流程定义对象 flow_definition
 *
 * @author warm
 * @since 2023-03-29
 */
public interface Definition extends RootEntity {

    @Override
    Long getId();

    @Override
    Definition setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    Definition setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    Definition setUpdateTime(Date updateTime);

    @Override
    String getCreateBy();

    @Override
    Definition setCreateBy(String createBy);

    @Override
    String getUpdateBy();

    @Override
    Definition setUpdateBy(String updateBy);

    @Override
    String getTenantId();

    @Override
    Definition setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    Definition setDelFlag(String delFlag);

    /**
     * 获取流程编码
     *
     * @return 流程编码
     */
    String getFlowCode();

    /**
     * 设置流程编码
     *
     * @param flowCode flowCode
     * @return Definition
     */
    Definition setFlowCode(String flowCode);

    /**
     * 获取流程名称
     *
     * @return 流程名称
     */
    String getFlowName();

    /**
     * 设置流程名称
     *
     * @param flowName flowName
     * @return Definition
     */
    Definition setFlowName(String flowName);

    /**
     * 设计器模型（CLASSICS经典模型 MIMIC仿钉钉模型）
     *
     * @return 设计器模型
     * @see org.dromara.warm.flow.core.enums.ModelEnum
     */
    String getModelValue();

    /**
     * 设置设计器模型。
     *
     * @param modelValue 设计器模型
     * @return 当前流程定义
     */
    Definition setModelValue(String modelValue);

    /**
     * 获取流程分类。
     *
     * @return 流程分类
     */
    String getCategory();

    /**
     * 设置流程分类。
     *
     * @param category 流程分类
     * @return 当前流程定义
     */
    Definition setCategory(String category);

    /**
     * 获取流程定义的版本号
     *
     * @return 版本号
     */
    String getVersion();

    /**
     * 设置流程定义版本号。
     *
     * @param version 版本号
     * @return 当前流程定义
     */
    Definition setVersion(String version);

    /**
     * 获取是否发布状态 (0未发布 1已发布 9已失效)
     *
     * @return 发布状态
     */
    Integer getIsPublish();

    /**
     * 设置发布状态。
     *
     * @param isPublish 发布状态
     * @return 当前流程定义
     */
    Definition setIsPublish(Integer isPublish);

    /**
     * 审批表单是否自定义（Y=是 N=否）
     *
     * @return 是否自定义
     */
    String getFormCustom();

    /**
     * 设置是否使用自定义表单。
     *
     * @param formCustom 是否自定义表单
     * @return 当前流程定义
     */
    Definition setFormCustom(String formCustom);

    /**
     * 获取自定义表单路径或表单标识。
     *
     * @return 表单路径或标识
     */
    String getFormPath();

    /**
     * 设置自定义表单路径或表单标识。
     *
     * @param formPath 表单路径或标识
     * @return 当前流程定义
     */
    Definition setFormPath(String formPath);

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
     * @return 当前流程定义
     */
    Definition setExt(String ext);

    /**
     * 获取流程定义下的节点列表。
     *
     * @return 节点列表
     */
    List<Node> getNodeList();

    /**
     * 设置流程定义下的节点列表。
     *
     * @param nodeList 节点列表
     * @return 当前流程定义
     */
    Definition setNodeList(List<Node> nodeList);

    /**
     * 获取流程定义关联的用户列表。
     *
     * @return 用户列表
     */
    List<User> getUserList();

    /**
     * 设置流程定义关联的用户列表。
     *
     * @param userList 用户列表
     * @return 当前流程定义
     */
    Definition setUserList(List<User> userList);

    /**
     * 流程激活状态（0=挂起 1=激活）
     *
     * @return 流程激活状态
     * @see org.dromara.warm.flow.core.enums.ActivityStatus
     */
    Integer getActivityStatus();

    /**
     * 设置流程激活状态。
     *
     * @param activityStatus 激活状态
     * @return 当前流程定义
     */
    Definition setActivityStatus(Integer activityStatus);

    /**
     * 获取监听器类型
     *
     * @return 监听器类型
     */
    String getListenerType();

    /**
     * 设置监听器类型。
     *
     * @param listenerType 监听器类型
     * @return 当前流程定义
     */
    Definition setListenerType(String listenerType);

    /**
     * 获取监听器路径
     *
     * @return 监听器路径
     */
    String getListenerPath();

    /**
     * 设置监听器路径。
     *
     * @param listenerPath 监听器路径
     * @return 当前流程定义
     */
    Definition setListenerPath(String listenerPath);

    /**
     * 复制流程定义基础属性。
     * <p>
     * 不复制主键、节点列表、用户列表和发布/激活状态，主要用于定义复制生成新版本。
     *
     * @return 新流程定义实体
     */
    default Definition copy() {
        return FlowEngine.newDef()
            .setTenantId(this.getTenantId())
            .setDelFlag(this.getDelFlag())
            .setFlowCode(this.getFlowCode())
            .setFlowName(this.getFlowName())
            .setModelValue(this.getModelValue())
            .setCategory(this.getCategory())
            .setVersion(this.getVersion())
            .setFormCustom(this.getFormCustom())
            .setFormPath(this.getFormPath())
            .setListenerType(this.getListenerType())
            .setListenerPath(this.getListenerPath())
            .setExt(this.getExt())
            .setCreateBy(this.getCreateBy())
            .setUpdateBy(this.getUpdateBy());

    }
}
