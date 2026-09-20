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
package org.dromara.warm.flow.core.orm.dao;

import org.dromara.warm.flow.core.entity.Definition;

import java.util.List;

/**
 * 流程定义 DAO 接口。
 * <p>
 * 定义表是流程版本、发布状态和流程图结构的入口。不同 ORM 扩展包实现该接口，
 * core 只依赖这里声明的查询和批量状态更新契约。
 *
 * @author warm
 * @since 2023-03-29
 */
public interface FlowDefinitionDao<T extends Definition> extends WarmDao<T> {

    /**
     * 根据流程编码集合批量查询定义。
     * <p>
     * 发布、导入、启动等场景会一次性加载同一批流程编码下的多个版本，
     * 具体排序规则由 ORM 实现与服务层共同保证。
     *
     * @param flowCodeList 流程编码集
     * @return 流程定义列表
     */
    List<T> queryByCodeList(List<String> flowCodeList);

    /**
     * 根据定义 ID 集合批量修改发布状态。
     * <p>
     * 发布新版本时用于将旧版本置为未发布或失效，属于流程定义生命周期的状态更新。
     *
     * @param ids           流程定义 ID 集合
     * @param publishStatus 发布状态(9=已失效；0=未发布；1=已发布)
     * @see org.dromara.warm.flow.core.enums.PublishStatus
     */
    void updatePublishStatus(List<Long> ids, Integer publishStatus);
}
