package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.User;
import org.dromara.warm.flow.core.orm.dao.FlowUserDao;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：任务办理人 DAO。
 *
 * @author warm
 */
public class UserMemDao<T extends User> extends InMemoryDao<T> implements FlowUserDao<T> {

    public UserMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    @Override
    public int deleteByTaskIds(List<Long> taskIds) {
        log.add(name() + ".deleteByTaskIds" + taskIds);
        List<Long> keys = store.values().stream().map(User.class::cast)
                .filter(u -> taskIds.contains(u.getAssociated()))
                .map(User::getId)
                .collect(Collectors.toList());
        keys.forEach(store::remove);
        return keys.size();
    }

    @Override
    public List<T> listByAssociatedAndTypes(List<Long> associatedList, String[] types) {
        log.add(name() + ".listByAssociatedAndTypes" + associatedList + "types=" + Arrays.toString(types));
        return store.values().stream()
                .filter(u -> associatedList.contains(((User) u).getAssociated()))
                .filter(u -> types == null || types.length == 0 || Arrays.asList(types).contains(((User) u).getType()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }

    @Override
    public List<T> listByProcessedBys(Long associated, List<String> processedBys, String[] types) {
        log.add(name() + ".listByProcessedBys[associated=" + associated + ",processedBys=" + processedBys
                + ",types=" + Arrays.toString(types) + "]");
        return store.values().stream()
                .filter(u -> Objects.equals(((User) u).getAssociated(), associated))
                .filter(u -> processedBys.contains(((User) u).getProcessedBy()))
                .filter(u -> types == null || types.length == 0 || Arrays.asList(types).contains(((User) u).getType()))
                .map(this::copyOf)
                .collect(Collectors.toList());
    }
}
