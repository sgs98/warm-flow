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
package org.dromara.warm.flow.core.handler;

import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.entity.RootEntity;
import org.dromara.warm.flow.core.utils.IdUtils;
import org.dromara.warm.flow.core.utils.ObjectUtil;
import org.dromara.warm.flow.core.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;

/**
 * 数据填充处理器。
 * <p>
 * 引擎在保存或更新实体前调用该扩展点，用于统一填充主键、创建时间、更新时间、
 * 创建人和更新人等审计字段。业务方可覆盖默认方法接入自己的 ID 生成或审计规则。
 *
 * @author warm
 * @since 2023/4/1 15:37
 */
public interface DataFillHandler {

    Logger logger = LoggerFactory.getLogger(DataFillHandler.class);

    /**
     * 新增前主键填充。
     * <p>
     * 默认在实体 ID 为空时使用 {@link IdUtils#nextId()} 生成主键。
     *
     * @param object 待填充实体
     */
    default void idFill(Object object) {
        RootEntity entity = (RootEntity) object;
        if (ObjectUtil.isNull(entity)) {
            logger.warn("Insert operation failed - Reason: Entity is null after casting");

            return;
        }
        if (Objects.isNull(entity.getId())) {
            entity.setId(IdUtils.nextId());
        }
    }

    /**
     * 新增前审计字段填充。
     * <p>
     * 默认填充创建时间、更新时间，并尝试从 {@link PermissionHandler} 获取当前办理人写入
     * createBy/updateBy；已有值不会被空值覆盖。
     *
     * @param object 待填充实体
     */
    default void insertFill(Object object) {
        RootEntity entity = (RootEntity) object;
        if (ObjectUtil.isNull(entity)) {
            logger.warn("Insert operation failed - Reason: Entity is null after casting");
            return;
        }
        entity.setCreateTime(ObjectUtil.isNotNull(entity.getCreateTime()) ? entity.getCreateTime() : new Date());
        entity.setUpdateTime(ObjectUtil.isNotNull(entity.getUpdateTime()) ? entity.getUpdateTime() : new Date());

        PermissionHandler permissionHandler = FlowEngine.permissionHandler();
        String handler = null;
        if (permissionHandler != null) {
            try {
                handler = permissionHandler.getHandler();
            } catch (Exception ignored) {
            }
        }
        entity.setCreateBy(StringUtils.isNotEmpty(handler) ? handler : entity.getCreateBy());
        entity.setUpdateBy(StringUtils.isNotEmpty(handler) ? handler : entity.getUpdateBy());
    }

    /**
     * 更新前审计字段填充。
     * <p>
     * 默认填充更新时间，并尝试从 {@link PermissionHandler} 获取当前办理人写入 updateBy。
     *
     * @param object 待填充实体
     */
    default void updateFill(Object object) {
        RootEntity entity = (RootEntity) object;
        if (ObjectUtil.isNull(entity)) {
            logger.warn("Insert operation failed - Reason: Entity is null after casting");
            return;
        }
        entity.setUpdateTime(ObjectUtil.isNotNull(entity.getUpdateTime()) ? entity.getUpdateTime() : new Date());
        PermissionHandler permissionHandler = FlowEngine.permissionHandler();
        String handler = null;
        if (permissionHandler != null) {
            try {
                handler = permissionHandler.getHandler();
            } catch (Exception ignored) {
            }
        }
        entity.setUpdateBy(StringUtils.isNotEmpty(handler) ? handler : entity.getUpdateBy());
    }
}
