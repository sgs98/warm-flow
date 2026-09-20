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

/**
 * 流程表单 flow_form
 *
 * @author vanlin
 * @since 2024/8/19 9:59
 */
public interface Form extends RootEntity {

    @Override
    Long getId();

    @Override
    Form setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    Form setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    Form setUpdateTime(Date updateTime);

    @Override
    String getCreateBy();

    @Override
    Form setCreateBy(String createBy);

    @Override
    String getUpdateBy();

    @Override
    Form setUpdateBy(String updateBy);

    @Override
    String getTenantId();

    @Override
    Form setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    Form setDelFlag(String delFlag);

    /**
     * 获取表单编码
     *
     * @return 表单编码
     */
    String getFormCode();

    /**
     * 设置表单编码。
     *
     * @param formCode 表单编码
     * @return 当前表单
     */
    Form setFormCode(String formCode);

    /**
     * 获取表单名称。
     *
     * @return 表单名称
     */
    String getFormName();

    /**
     * 设置表单名称。
     *
     * @param formName 表单名称
     * @return 当前表单
     */
    Form setFormName(String formName);

    /**
     * 获取表单版本号。
     *
     * @return 表单版本号
     */
    String getVersion();

    /**
     * 设置表单版本号。
     *
     * @param version 表单版本号
     * @return 当前表单
     */
    Form setVersion(String version);

    /**
     * 是否发布（0未发布 1已发布 9失效）
     *
     * @return 发布状态
     */
    Integer getIsPublish();

    /**
     * 设置发布状态。
     *
     * @param isPublish 发布状态
     * @return 当前表单
     */
    Form setIsPublish(Integer isPublish);

    /**
     * 表单类型（0内置表单 存 form_content        1外挂表单 存form_path）
     *
     * @return 表单类型
     */
    Integer getFormType();

    /**
     * 设置表单类型。
     *
     * @param formType 表单类型
     * @return 当前表单
     */
    Form setFormType(Integer formType);

    /**
     * 获取内置表单内容。
     *
     * @return 表单内容
     */
    String getFormContent();

    /**
     * 设置内置表单内容。
     *
     * @param formContent 表单内容
     * @return 当前表单
     */
    Form setFormContent(String formContent);

    /**
     * 获取外挂表单路径或表单标识。
     *
     * @return 表单路径或标识
     */
    String getFormPath();

    /**
     * 设置外挂表单路径或表单标识。
     *
     * @param formPath 表单路径或标识
     * @return 当前表单
     */
    Form setFormPath(String formPath);

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
     * @return 当前表单
     */
    Form setExt(String ext);
}
