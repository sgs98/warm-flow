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

import java.io.Serializable;
import java.util.Date;

/**
 * 流程实体基础接口。
 * <p>
 * 所有 core 实体共享主键、创建时间、更新时间、租户和逻辑删除字段。
 * createBy/updateBy 对部分实体不是强制字段，因此提供默认空实现以保持实体接口兼容。
 *
 * @author warm
 * @since 2023/5/17 17:23
 */
public interface RootEntity extends Serializable {

    /**
     * 获取主键。
     *
     * @return 主键 ID
     */
    Long getId();

    /**
     * 设置主键。
     *
     * @param id 主键 ID
     * @return 当前实体
     */
    RootEntity setId(Long id);

    /**
     * 获取创建时间。
     *
     * @return 创建时间
     */
    Date getCreateTime();

    /**
     * 设置创建时间。
     *
     * @param createTime 创建时间
     * @return 当前实体
     */
    RootEntity setCreateTime(Date createTime);

    /**
     * 获取更新时间。
     *
     * @return 更新时间
     */
    Date getUpdateTime();

    /**
     * 设置更新时间。
     *
     * @param updateTime 更新时间
     * @return 当前实体
     */
    RootEntity setUpdateTime(Date updateTime);

    /**
     * 获取创建人；实体不支持该字段时返回 {@code null}。
     *
     * @return 创建人
     */
    default String getCreateBy() {
        return null;
    }

    /**
     * 设置创建人；实体不支持该字段时保持原对象不变。
     *
     * @param createBy 创建人
     * @return 当前实体
     */
    default RootEntity setCreateBy(String createBy) {
        return this;
    }

    /**
     * 获取更新人；实体不支持该字段时返回 {@code null}。
     *
     * @return 更新人
     */
    default String getUpdateBy() {
        return null;
    }

    /**
     * 设置更新人；实体不支持该字段时保持原对象不变。
     *
     * @param updateBy 更新人
     * @return 当前实体
     */
    default RootEntity setUpdateBy(String updateBy) {
        return this;
    }

    /**
     * 获取租户 ID。
     *
     * @return 租户 ID
     */
    String getTenantId();

    /**
     * 设置租户 ID。
     *
     * @param tenantId 租户 ID
     * @return 当前实体
     */
    RootEntity setTenantId(String tenantId);

    /**
     * 获取逻辑删除标识。
     *
     * @return 逻辑删除标识
     */
    String getDelFlag();

    /**
     * 设置逻辑删除标识。
     *
     * @param delFlag 逻辑删除标识
     * @return 当前实体
     */
    RootEntity setDelFlag(String delFlag);

}
