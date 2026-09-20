package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.Instance;
import org.dromara.warm.flow.core.orm.dao.FlowInstanceDao;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：流程实例 DAO。
 *
 * @author warm
 */
public class InstanceMemDao<T extends Instance> extends InMemoryDao<T> implements FlowInstanceDao<T> {

    /**
     * 创建流程实例内存 DAO，并复用基类的实体工厂与调用日志。
     */
    public InstanceMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    /**
     * 按流程定义 ID 集合查询流程实例。
     */
    @Override
    public List<T> getByDefIds(List<Long> defIds) {
        log.add(name() + ".getByDefIds" + defIds);
        return store.values().stream()
                .filter(i -> defIds.contains(((Instance) i).getDefinitionId()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }
}
