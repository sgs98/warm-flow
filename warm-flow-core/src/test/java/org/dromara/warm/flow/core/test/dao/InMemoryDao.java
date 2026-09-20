package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.RootEntity;
import org.dromara.warm.flow.core.orm.agent.WarmQuery;
import org.dromara.warm.flow.core.orm.dao.WarmDao;
import org.dromara.warm.flow.core.utils.page.Page;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存 DAO 基类：模拟 SQL 行语义——
 * <ul>
 *   <li>selectList(entity) 按非空列等值匹配；集合/Map 属性视为瞬态关联，不参与 where；</li>
 *   <li>读路径返回列字段拷贝：瞬态字段（如 task.userList）每次查询后回到未加载状态，与 ORM 行为一致；</li>
 *   <li>写路径向调用者实体回填主键（模拟 MyBatis insert 回写），updateById 按非空列合并到既有行。</li>
 * </ul>
 * 所有读写调用记录进共享 log，供特征测试断言查询次数与持久化顺序。
 *
 * @author warm
 */
public abstract class InMemoryDao<T extends RootEntity> implements WarmDao<T> {

    protected final Map<Long, T> store = new LinkedHashMap<>();
    private final AtomicLong idGen = new AtomicLong(10_000);
    private final Supplier<T> entityFactory;
    protected final List<String> log;
    private volatile List<Method> columnGetters;

    /**
     * 绑定实体工厂与共享调用日志，所有子类共用同一套内存行存储语义。
     */
    protected InMemoryDao(Supplier<T> entityFactory, List<String> log) {
        this.entityFactory = entityFactory;
        this.log = log;
    }

    /**
     * 返回 DAO 简名，用于构造可读的调用日志。
     */
    protected String name() {
        return getClass().getSimpleName();
    }

    /**
     * 返回当前内存表行数，供测试直接断言持久化副作用。
     */
    public int size() {
        return store.size();
    }

    /**
     * 直接读取原始行对象，不记录查询日志、不执行列拷贝。
     */
    public T raw(Serializable id) {
        return store.get(id);
    }

    /**
     * 直接删除原始行对象，用于测试准备特殊存储状态。
     */
    public T removeRaw(Serializable id) {
        return store.remove(id);
    }

    /**
     * 返回当前内存表所有原始行的快照列表。
     */
    public List<T> all() {
        return new ArrayList<>(store.values());
    }

    /**
     * 创建 DAO 对应的测试实体实例，模拟 ORM 适配层实体供应能力。
     */
    @Override
    public T newEntity() {
        return entityFactory.get();
    }

    /**
     * 按主键查询单行，返回列字段拷贝以避免测试误依赖瞬态对象引用。
     */
    @Override
    public T selectById(Serializable id) {
        log.add(name() + ".selectById[" + id + "]");
        T row = store.get(id);
        return row == null ? null : copyOf(row);
    }

    /**
     * 按主键集合查询多行，仅返回存在的记录并保持入参顺序。
     */
    @Override
    public List<T> selectByIds(Collection<? extends Serializable> ids) {
        log.add(name() + ".selectByIds" + ids);
        return ids.stream().map(store::get).filter(Objects::nonNull).map(this::copyOf).collect(Collectors.toList());
    }

    /**
     * 按非空字段分页查询，模拟服务层分页接口依赖的 total 与 list 回填。
     */
    @Override
    public Page<T> selectPage(T entity, Page<T> page) {
        log.add(name() + ".selectPage" + criteria(entity));
        List<T> matched = selectQuietly(entity).stream().map(this::copyOf).collect(Collectors.toList());
        int from = Math.max(0, (page.getPageNum() - 1) * page.getPageSize());
        int to = Math.min(matched.size(), from + Math.max(page.getPageSize(), 0));
        page.setTotal(matched.size());
        page.setList(from >= matched.size() ? new ArrayList<>() : new ArrayList<>(matched.subList(from, to)));
        return page;
    }

    /**
     * 按查询实体的非空字段等值匹配列表，WarmQuery 在测试内暂不参与过滤。
     */
    @Override
    public List<T> selectList(T entity, WarmQuery<T> query) {
        log.add(name() + ".selectList" + criteria(entity));
        return selectQuietly(entity).stream().map(this::copyOf).collect(Collectors.toList());
    }

    /**
     * 按查询实体的非空字段统计匹配行数。
     */
    @Override
    public long selectCount(T entity) {
        log.add(name() + ".selectCount" + criteria(entity));
        return selectQuietly(entity).size();
    }

    /**
     * 内部无日志查询入口，供分页、删除等组合操作复用。
     */
    private List<T> selectQuietly(T entity) {
        if (entity == null) {
            return new ArrayList<>(store.values());
        }
        return store.values().stream().filter(t -> matches(entity, t)).collect(Collectors.toList());
    }

    /**
     * 保存单行并回填主键，存储端保留列字段拷贝来模拟数据库行。
     */
    @Override
    public int save(T entity) {
        log.add(name() + ".save" + criteria(entity));
        if (entity.getId() == null) {
            entity.setId(idGen.incrementAndGet());
        }
        store.put(entity.getId(), copyOf(entity));
        return 1;
    }

    /**
     * 按主键更新单行，只合并非空列以贴近默认 ORM 更新策略。
     */
    @Override
    public int updateById(T entity) {
        log.add(name() + ".updateById[" + entity.getId() + "]");
        T row = store.get(entity.getId());
        if (row == null) {
            return 0;
        }
        mergeNonNull(entity, row);
        return 1;
    }

    /**
     * 按查询实体的非空字段删除匹配行，返回实际删除数量。
     */
    @Override
    public int delete(T entity) {
        log.add(name() + ".delete" + criteria(entity));
        List<Long> keys = selectQuietly(entity).stream().map(RootEntity::getId).collect(Collectors.toList());
        keys.forEach(store::remove);
        return keys.size();
    }

    /**
     * 按主键删除单行，返回是否删除成功的计数。
     */
    @Override
    public int deleteById(Serializable id) {
        log.add(name() + ".deleteById[" + id + "]");
        return store.remove(id) != null ? 1 : 0;
    }

    /**
     * 按主键集合批量删除，返回实际删除数量。
     */
    @Override
    public int deleteByIds(Collection<? extends Serializable> ids) {
        log.add(name() + ".deleteByIds" + ids);
        int removed = 0;
        for (Serializable id : ids) {
            removed += store.remove(id) != null ? 1 : 0;
        }
        return removed;
    }

    /**
     * 批量保存并逐条回填主键，保持与单行保存一致的行拷贝语义。
     */
    @Override
    public void saveBatch(List<T> list) {
        log.add(name() + ".saveBatch[n=" + list.size() + "]");
        for (T entity : list) {
            if (entity.getId() == null) {
                entity.setId(idGen.incrementAndGet());
            }
            store.put(entity.getId(), copyOf(entity));
        }
    }

    /**
     * 批量按主键更新，存在的行执行非空列合并，不存在的行忽略。
     */
    @Override
    public void updateBatch(List<T> list) {
        log.add(name() + ".updateBatch[n=" + list.size() + "]");
        for (T entity : list) {
            T row = store.get(entity.getId());
            if (row != null) {
                mergeNonNull(entity, row);
            }
        }
    }

    /**
     * 复制持久化列字段：集合、Map、数组等瞬态关联字段保持未加载状态。
     */
    protected T copyOf(T src) {
        T dst = entityFactory.get();
        for (Method getter : columnGetters()) {
            try {
                Object v = getter.invoke(src);
                if (v == null) {
                    continue;
                }
                String setterName = "set" + getter.getName().substring(3);
                for (Method m : dst.getClass().getMethods()) {
                    if (m.getName().equals(setterName) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(getter.getReturnType())) {
                        m.invoke(dst, v);
                        break;
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("内存 DAO 拷贝失败: " + getter, e);
            }
        }
        return dst;
    }

    /**
     * 按非空列合并，模拟 MyBatis-Plus updateById 的 NOT_NULL 更新策略。
     */
    private void mergeNonNull(T src, T row) {
        for (Method getter : columnGetters()) {
            try {
                Object v = getter.invoke(src);
                if (v == null) {
                    continue;
                }
                String setterName = "set" + getter.getName().substring(3);
                for (Method m : row.getClass().getMethods()) {
                    if (m.getName().equals(setterName) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(getter.getReturnType())) {
                        m.invoke(row, v);
                        break;
                    }
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("内存 DAO 合并失败: " + getter, e);
            }
        }
    }

    /**
     * 判断候选行是否满足查询实体的所有非空列条件。
     */
    private boolean matches(T criterion, T candidate) {
        for (Method getter : columnGetters()) {
            try {
                Object left = getter.invoke(criterion);
                if (left == null) {
                    continue;
                }
                if (!left.equals(getter.invoke(candidate))) {
                    return false;
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("内存 DAO 反射取值失败: " + getter, e);
            }
        }
        return true;
    }

    /**
     * 缓存可持久化列的 getter，过滤集合类瞬态关联字段。
     */
    private List<Method> columnGetters() {
        if (columnGetters == null) {
            List<Method> getters = new ArrayList<>();
            for (Method m : entityFactory.get().getClass().getMethods()) {
                if (m.getParameterCount() != 0 || !m.getName().startsWith("get") || "getClass".equals(m.getName())) {
                    continue;
                }
                Class<?> rt = m.getReturnType();
                if (Collection.class.isAssignableFrom(rt) || Map.class.isAssignableFrom(rt) || rt.isArray()) {
                    continue;
                }
                getters.add(m);
            }
            columnGetters = getters;
        }
        return columnGetters;
    }

    /**
     * 将查询实体中的非空列格式化为日志片段，便于测试断言调用路径。
     */
    private String criteria(T entity) {
        if (entity == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (Method m : columnGetters()) {
            try {
                Object v = m.invoke(entity);
                if (v != null) {
                    if (sb.length() > 1) {
                        sb.append(",");
                    }
                    sb.append(m.getName(), 3, m.getName().length()).append('=').append(v);
                }
            } catch (ReflectiveOperationException ignored) {
                // 仅用于日志展示，忽略
            }
        }
        return sb.append("]").toString();
    }
}
