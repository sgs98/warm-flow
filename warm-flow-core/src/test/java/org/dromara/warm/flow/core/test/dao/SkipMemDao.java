package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.orm.dao.FlowSkipDao;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：节点连线 DAO。
 *
 * @author warm
 */
public class SkipMemDao<T extends Skip> extends InMemoryDao<T> implements FlowSkipDao<T> {

    public SkipMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    @Override
    public int deleteSkipByDefIds(Collection<? extends Serializable> defIds) {
        log.add(name() + ".deleteSkipByDefIds" + defIds);
        Set<Long> ids = defIds.stream().map(id -> Long.parseLong(String.valueOf(id))).collect(Collectors.toSet());
        List<Long> keys = store.values().stream().map(Skip.class::cast)
                .filter(s -> ids.contains(s.getDefinitionId()))
                .map(Skip::getId)
                .collect(Collectors.toList());
        keys.forEach(store::remove);
        return keys.size();
    }
}
