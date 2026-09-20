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
package org.dromara.warm.flow.core.orm.service.impl;


import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.handler.DataFillHandler;
import org.dromara.warm.flow.core.orm.agent.WarmQuery;
import org.dromara.warm.flow.core.orm.dao.WarmDao;
import org.dromara.warm.flow.core.orm.service.IWarmService;
import org.dromara.warm.flow.core.utils.CollUtil;
import org.dromara.warm.flow.core.utils.SqlHelper;
import org.dromara.warm.flow.core.utils.page.Page;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 流程实体通用服务基础实现。
 * <p>
 * 该类把 {@link IWarmService} 的统一服务语义委托给具体 ORM DAO，并集中处理：
 * 查询转发、受影响行数到布尔结果的转换、数据填充、空集合短路、批量拆分以及排序查询对象创建。
 * 具体流程服务只需绑定对应的 DAO，并在此基础上实现流程领域方法。
 * <p>
 * 本类不依赖具体 ORM、容器或数据库 API，所有持久化差异通过 {@link WarmDao} 扩展点注入。
 *
 * @author warm
 * @since 2023-03-17
 */
public abstract class WarmServiceImpl<M extends WarmDao<T>, T> implements IWarmService<T> {

    /**
     * 当前服务绑定的实体 DAO。
     */
    protected M warmDao;

    /**
     * 返回当前服务绑定的 DAO。
     *
     * @return 当前 DAO
     */
    @Override
    public M getDao() {
        return warmDao;
    }

    /**
     * 设置当前服务使用的 DAO。
     * <p>
     * 由具体服务实现完成类型化绑定，通常在引擎装配阶段调用。
     *
     * @param warmDao 实体 DAO
     * @return 当前服务实例
     */
    protected abstract IWarmService<T> setDao(M warmDao);

    /**
     * 委托 DAO 按主键查询实体。
     *
     * @param id 主键
     * @return 实体，不存在时返回 {@code null}
     */
    @Override
    public T getById(Serializable id) {
        return getDao().selectById(id);
    }

    /**
     * 委托 DAO 按主键集合批量查询实体。
     *
     * @param ids 主键集合
     * @return 实体列表
     */
    @Override
    public List<T> getByIds(Collection<? extends Serializable> ids) {
        return getDao().selectByIds(ids);
    }

    /**
     * 委托 DAO 执行分页查询，并回填传入的分页对象。
     *
     * @param entity 查询条件实体
     * @param page 分页参数与结果承载对象
     * @return 分页查询结果
     */
    @Override
    public Page<T> page(T entity, Page<T> page) {
        return getDao().selectPage(entity, page);
    }

    /**
     * 使用实体非空字段查询列表，不附加扩展查询条件。
     *
     * @param entity 查询条件实体
     * @return 匹配的实体列表
     */
    @Override
    public List<T> list(T entity) {
        return getDao().selectList(entity, null);
    }

    /**
     * 使用实体非空字段和扩展查询条件查询列表。
     *
     * @param entity 查询条件实体
     * @param query 扩展查询条件
     * @return 匹配的实体列表
     */
    @Override
    public List<T> list(T entity, WarmQuery<T> query) {
        return getDao().selectList(entity, query);
    }

    /**
     * 查询实体列表并提取一条记录。
     *
     * @param entity 查询条件实体
     * @return 匹配的唯一实体，不存在时返回 {@code null}
     */
    @Override
    public T getOne(T entity) {
        List<T> list = getDao().selectList(entity, null);
        return CollUtil.getOne(list);
    }

    /**
     * 委托 DAO 统计匹配记录数。
     *
     * @param entity 查询条件实体
     * @return 匹配记录数
     */
    @Override
    public long selectCount(T entity) {
        return getDao().selectCount(entity);
    }

    /**
     * 根据统计数量判断是否存在匹配记录。
     *
     * @param entity 查询条件实体
     * @return 存在匹配记录时返回 {@code true}
     */
    @Override
    public Boolean exists(T entity) {
        long count = selectCount(entity);
        return count > 0;
    }

    /**
     * 执行新增数据填充后委托 DAO 保存实体。
     *
     * @param entity 待新增实体
     * @return 新增是否成功
     */
    @Override
    public boolean save(T entity) {
        insertFill(entity);
        return SqlHelper.retBool(getDao().save(entity));
    }

    /**
     * 执行更新数据填充后按主键更新实体。
     *
     * @param entity 待更新实体
     * @return 更新是否成功
     */
    @Override
    public boolean updateById(T entity) {
        updateFill(entity);
        return SqlHelper.retBool(getDao().updateById(entity));
    }

    /**
     * 委托 DAO 按主键删除实体，并将受影响行数转换为布尔结果。
     *
     * @param id 主键
     * @return 删除是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        return SqlHelper.retBool(getDao().deleteById(id));
    }

    /**
     * 委托 DAO 按实体非空字段删除匹配记录。
     *
     * @param entity 删除条件实体
     * @return 删除是否成功
     */
    @Override
    public boolean remove(T entity) {
        return SqlHelper.retBool(getDao().delete(entity));
    }

    /**
     * 委托 DAO 按主键集合批量删除实体。
     *
     * @param ids 主键集合
     * @return 是否至少删除一条记录
     */
    @Override
    public boolean removeByIds(Collection<? extends Serializable> ids) {
        return SqlHelper.retBool(getDao().deleteByIds(ids));
    }

    /**
     * 使用默认批次大小批量新增实体。
     * <p>
     * 默认批次大小为 1000；空集合直接返回，不触发 DAO 调用。
     *
     * @param list 待新增实体集合
     */
    @Override
    public void saveBatch(List<T> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        this.saveBatch(list, 1000);
    }

    /**
     * 按指定批次大小拆分并批量新增实体。
     * <p>
     * 每个实体在写入前执行新增数据填充；传入非正批次大小时使用 1000。
     * 空集合直接返回，不触发数据填充或 DAO 调用。
     *
     * @param list 待新增实体集合
     * @param batchSize 单批最大记录数
     */
    @Override
    public void saveBatch(List<T> list, int batchSize) {
        if (CollUtil.isEmpty(list)) {
            return;
        }

        List<List<T>> split = CollUtil.split(list, batchSize > 0 ? batchSize : 1000);

        for (List<T> ts : split) {
            ts.forEach(this::insertFill);
            getDao().saveBatch(ts);
        }
    }

    /**
     * 批量执行更新数据填充并委托 DAO 按主键更新。
     * <p>
     * 空集合直接返回，不触发数据填充或 DAO 调用。
     *
     * @param list 待更新实体集合
     */
    @Override
    public void updateBatch(List<T> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        list.forEach(this::updateFill);
        getDao().updateBatch(list);
    }

    /**
     * 创建按主键升序排列的查询对象。
     *
     * @return 查询对象
     */
    @Override
    public WarmQuery<T> orderById() {
        return new WarmQuery<>(this).orderById();
    }

    /**
     * 创建按创建时间升序排列的查询对象。
     *
     * @return 查询对象
     */
    @Override
    public WarmQuery<T> orderByCreateTime() {
        return new WarmQuery<>(this).orderByCreateTime();
    }

    /**
     * 创建按更新时间升序排列的查询对象。
     *
     * @return 查询对象
     */
    @Override
    public WarmQuery<T> orderByUpdateTime() {
        return new WarmQuery<>(this).orderByUpdateTime();
    }

    /**
     * 创建按指定字段升序排列的查询对象。
     *
     * @param orderByField 排序字段名
     * @return 查询对象
     */
    @Override
    public WarmQuery<T> orderByAsc(String orderByField) {
        return new WarmQuery<>(this).orderByAsc(orderByField);
    }

    /**
     * 创建按指定字段降序排列的查询对象。
     *
     * @param orderByField 排序字段名
     * @return 查询对象
     */
    @Override
    public WarmQuery<T> orderByDesc(String orderByField) {
        return new WarmQuery<>(this).orderByDesc(orderByField);
    }

    /**
     * 创建使用自定义排序配置的查询对象。
     *
     * @param orderByField 自定义排序字段或表达式
     * @return 查询对象
     */
    @Override
    public WarmQuery<T> orderBy(String orderByField) {
        return new WarmQuery<>(this).orderBy(orderByField);
    }

    /**
     * 执行新增前的数据填充。
     * <p>
     * 未配置 {@link DataFillHandler} 时保持实体原值不变；配置后由处理器负责主键、
     * 创建时间、创建人等新增字段的填充。
     *
     * @param entity 待填充实体
     */
    public void insertFill(T entity) {
        DataFillHandler dataFillHandler = FlowEngine.dataFillHandler();
        if (dataFillHandler == null) {
            return;
        }
        dataFillHandler.idFill(entity);
        dataFillHandler.insertFill(entity);
    }

    /**
     * 执行更新前的数据填充。
     * <p>
     * 未配置 {@link DataFillHandler} 时保持实体原值不变；配置后由处理器负责更新时间、
     * 更新人等更新字段的填充。
     *
     * @param entity 待填充实体
     */
    public void updateFill(T entity) {
        DataFillHandler dataFillHandler = FlowEngine.dataFillHandler();
        if (dataFillHandler == null) {
            return;
        }
        dataFillHandler.updateFill(entity);
    }
}
