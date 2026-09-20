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
 * 流程用户 flow_user
 *
 * @author xiarg
 * @since 2024/5/10 10:41
 */
public interface User extends RootEntity {

    @Override
    Long getId();

    @Override
    User setId(Long id);

    @Override
    Date getCreateTime();

    @Override
    User setCreateTime(Date createTime);

    @Override
    Date getUpdateTime();

    @Override
    User setUpdateTime(Date updateTime);

    @Override
    String getCreateBy();

    @Override
    User setCreateBy(String createBy);

    @Override
    String getUpdateBy();

    @Override
    User setUpdateBy(String updateBy);

    @Override
    String getTenantId();

    @Override
    User setTenantId(String tenantId);

    @Override
    String getDelFlag();

    @Override
    User setDelFlag(String delFlag);

    /**
     * 获取人员类型
     *
     * @return 人员类型（1待办任务的审批人权限 2待办任务的转办人权限 3待办任务的委托人权限）
     * @see org.dromara.warm.flow.core.enums.UserType
     */
    String getType();

    /**
     * 设置人员类型。
     *
     * @param type 人员类型
     * @return 当前用户记录
     */
    User setType(String type);

    /**
     * 获取 权限人
     *
     * @return 权限人
     */
    String getProcessedBy();

    /**
     * 设置权限人或已处理人标识。
     *
     * @param processedBy 权限人或已处理人标识
     * @return 当前用户记录
     */
    User setProcessedBy(String processedBy);

    /**
     * 获取关联业务表 ID。
     *
     * @return 关联业务表 ID
     */
    Long getAssociated();

    /**
     * 设置关联业务表 ID。
     * <p>
     * 当前字段可关联待办任务、历史任务、流程实例或节点等记录，具体含义由 {@link #getType()} 区分。
     *
     * @param associated 关联业务表 ID
     * @return 当前用户记录
     */
    User setAssociated(Long associated);
}
