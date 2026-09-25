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

import org.dromara.warm.flow.core.orm.agent.WarmQuery;
import org.dromara.warm.flow.core.utils.page.Page;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 基础 DAO 接口。
 * <p>
 * core 通过该接口屏蔽具体 ORM 差异，MyBatis、MyBatis-Plus 等适配模块负责把这里的
 * 通用 CRUD 语义映射到真实数据库操作。入参中的实体通常作为「非空字段等值条件」或
 * 「待保存/更新行」使用，租户、逻辑删除、数据填充等增强由具体 DAO 实现按引擎配置处理。
 *
 * @author warm
 * @since 2023-03-17
 */
public interface WarmDao<T> {

    /**
     * 创建当前 DAO 对应的实体实例。
     * <p>
     * core 不直接依赖 ORM 实体实现，需要通过适配层提供实体构造能力，用于查询条件、
     * 新增行、租户/逻辑删除条件承载等场景。
     *
     * @return 新实体实例
     */
    T newEntity();

    /**
     * 根据主键查询单条记录。
     *
     * @param id 主键
     * @return 实体记录，不存在时返回 {@code null}
     */
    T selectById(Serializable id);

    /**
     * 根据主键集合批量查询记录。
     *
     * @param ids 主键集合
     * @return 实体列表
     */
    List<T> selectByIds(Collection<? extends Serializable> ids);

    /**
     * 根据实体非空字段分页查询。
     *
     * @param entity 查询条件实体
     * @param page   分页参数与结果承载对象
     * @return 回填总数和当前页数据后的分页对象
     */
    Page<T> selectPage(T entity, Page<T> page);

    /**
     * 根据实体非空字段和扩展查询条件查询列表。
     *
     * @param entity 查询条件实体
     * @param query  ORM 无关的扩展查询条件，可为 {@code null}
     * @return 匹配的实体列表
     */
    List<T> selectList(T entity, WarmQuery<T> query);

    /**
     * 根据实体非空字段统计记录数。
     *
     * @param entity 查询条件实体
     * @return 匹配记录数
     */
    long selectCount(T entity);

    /**
     * 新增单条记录。
     *
     * @param entity 待新增实体
     * @return 受影响行数
     */
    int save(T entity);

    /**
     * 根据实体主键更新记录。
     *
     * @param entity 待更新实体，必须携带主键
     * @return 受影响行数
     */
    int updateById(T entity);

    /**
     * 根据实体非空字段删除匹配记录。
     *
     * @return 受影响行数
     */
    int delete(T entity);

    /**
     * 根据主键删除单条记录。
     *
     * @param id 主键
     * @return 受影响行数
     */
    int deleteById(Serializable id);

    /**
     * 根据主键集合批量删除记录。
     *
     * @param ids 需要删除的数据主键集合
     * @return 受影响行数
     */
    int deleteByIds(Collection<? extends Serializable> ids);

    /**
     * 批量新增记录。
     *
     * @param list 待新增实体集合
     */
    void saveBatch(List<T> list);

    /**
     * 批量按主键更新记录。
     *
     * @param list 待更新实体集合
     */
    void updateBatch(List<T> list);
}
