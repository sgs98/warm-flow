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
package org.dromara.warm.flow.core.orm.service;


import org.dromara.warm.flow.core.orm.agent.WarmQuery;
import org.dromara.warm.flow.core.orm.dao.WarmDao;
import org.dromara.warm.flow.core.utils.page.Page;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 流程实体通用服务接口。
 * <p>
 * 该接口位于 core 服务层与 ORM DAO 层之间，为流程定义、节点、连线、实例、任务、
 * 历史任务、办理人和表单等实体提供统一的数据访问能力。具体业务服务在此基础上
 * 增加流程引擎专属操作，底层持久化细节由 {@link WarmDao} 的 ORM 适配实现负责。
 * <p>
 * 查询方法使用实体中的非空字段作为基础条件；写入方法返回业务层更易使用的布尔结果，
 * 批量方法由实现统一处理空集合、分批写入和数据填充。
 *
 * @author warm
 * @since 2023-03-17
 */
public interface IWarmService<T> {

    /**
     * 获取当前服务绑定的 DAO。
     * <p>
     * 泛型返回类型允许具体服务直接使用对应实体的扩展 DAO 方法。
     *
     * @param <M> DAO 类型
     * @return 当前服务使用的 DAO
     */
    <M extends WarmDao<T>> M getDao();

    /**
     * 根据主键查询实体。
     *
     * @param id 主键
     * @return 实体，不存在时返回 {@code null}
     */
    T getById(Serializable id);

    /**
     * 根据主键集合批量查询实体。
     *
     * @param ids 主键集合
     * @return 实体列表
     */
    List<T> getByIds(Collection<? extends Serializable> ids);

    /**
     * 按实体非空字段分页查询。
     *
     * @param entity 查询实体
     * @param page   分页参数与结果承载对象
     * @return 回填总数和当前页数据后的分页对象
     */
    Page<T> page(T entity, Page<T> page);

    /**
     * 按实体非空字段查询列表。
     *
     * @param entity 查询实体
     * @return 匹配的实体列表
     */
    List<T> list(T entity);

    /**
     * 按实体非空字段和扩展查询条件查询列表。
     *
     * @param entity 查询实体
     * @param query  查询条件，可包含排序信息
     * @return 匹配的实体列表
     */
    List<T> list(T entity, WarmQuery<T> query);

    /**
     * 查询一条匹配记录。
     * <p>
     * 具体实现沿用 core 的单条记录提取规则；调用方应确保查询条件具有足够的唯一性。
     *
     * @param entity 查询实体
     * @return 匹配的实体，不存在时返回 {@code null}
     */
    T getOne(T entity);

    /**
     * 统计匹配实体数量。
     *
     * @param entity 查询实体
     * @return 匹配记录数
     */
    long selectCount(T entity);

    /**
     * 判断是否存在匹配实体。
     *
     * @param entity 查询实体
     * @return 存在匹配记录时返回 {@code true}
     */
    Boolean exists(T entity);

    /**
     * 新增实体，并在启用数据填充时执行主键、创建信息等新增填充。
     *
     * @param entity 待新增实体
     * @return 新增成功返回 {@code true}
     */
    boolean save(T entity);

    /**
     * 根据实体主键更新记录，并在启用数据填充时执行更新信息填充。
     *
     * @param entity 待更新实体，必须携带主键
     * @return 更新成功返回 {@code true}
     */
    boolean updateById(T entity);

    /**
     * 根据主键删除实体。
     *
     * @param id 主键
     * @return 删除成功返回 {@code true}
     */
    boolean removeById(Serializable id);

    /**
     * 根据实体非空字段删除匹配记录。
     *
     * @param entity 删除条件实体
     * @return 删除成功返回 {@code true}
     */
    boolean remove(T entity);

    /**
     * 根据主键集合批量删除实体。
     *
     * @param ids 待删除实体的主键集合
     * @return 至少删除一条记录时返回 {@code true}
     */
    boolean removeByIds(Collection<? extends Serializable> ids);

    /**
     * 批量新增实体，使用实现类约定的默认批次大小。
     *
     * @param list 待新增实体集合
     */
    void saveBatch(List<T> list);

    /**
     * 按指定批次大小批量新增实体。
     * <p>
     * 实现会先按批次拆分，再对每个实体执行新增数据填充；非正批次大小使用实现约定的默认值。
     *
     * @param list      待新增实体集合
     * @param batchSize 单批最大记录数
     */
    void saveBatch(List<T> list, int batchSize);

    /**
     * 批量按主键更新实体。
     * <p>
     * 实现会在提交 DAO 前统一执行更新数据填充。
     *
     * @param list 待更新实体集合
     */
    void updateBatch(List<T> list);

    /**
     * 创建按主键升序排列的查询条件。
     *
     * @return 可继续追加条件的查询对象
     */
    WarmQuery<T> orderById();

    /**
     * 创建按创建时间升序排列的查询条件。
     *
     * @return 可继续追加条件的查询对象
     */
    WarmQuery<T> orderByCreateTime();

    /**
     * 创建按更新时间升序排列的查询条件。
     *
     * @return 可继续追加条件的查询对象
     */
    WarmQuery<T> orderByUpdateTime();

    /**
     * 创建按指定字段升序排列的查询条件。
     *
     * @param orderByField 排序字段名
     * @return 可继续追加条件的查询对象
     */
    WarmQuery<T> orderByAsc(String orderByField);

    /**
     * 创建按指定字段降序排列的查询条件。
     *
     * @param orderByField 排序字段名
     * @return 可继续追加条件的查询对象
     */
    WarmQuery<T> orderByDesc(String orderByField);

    /**
     * 创建使用自定义排序片段的查询条件。
     * <p>
     * 排序字段或表达式的合法性由具体 ORM 实现负责校验和转换，调用方应传入受信任的字段配置。
     *
     * @param orderByField 自定义排序字段或排序表达式
     * @return 可继续追加条件的查询对象
     */
    WarmQuery<T> orderBy(String orderByField);
}
