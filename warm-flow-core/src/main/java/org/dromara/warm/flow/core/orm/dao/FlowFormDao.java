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

import org.dromara.warm.flow.core.entity.Form;

import java.util.List;

/**
 * 流程表单 DAO 接口。
 * <p>
 * 表单与流程定义一样存在编码和版本概念，不同 ORM 扩展包负责实现查询条件、
 * 租户与逻辑删除等数据库细节。
 *
 * @author vanlin
 * @className FlowFormDao
 * @description
 * @since 2024/8/19 10:24
 */
public interface FlowFormDao<T extends Form> extends WarmDao<T> {

    /**
     * 根据表单编码集合批量查询表单版本。
     * <p>
     * 表单保存、复制、发布时需要按编码加载已有版本，用于计算下一版本号或校验唯一性。
     *
     * @param formCodeList 表单编码集合
     * @return 表单列表
     */
    List<T> queryByCodeList(List<String> formCodeList);
}
