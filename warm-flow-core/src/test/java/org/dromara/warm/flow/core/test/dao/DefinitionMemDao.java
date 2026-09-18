package org.dromara.warm.flow.core.test.dao;

import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.orm.dao.FlowDefinitionDao;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 单测内存实现：流程定义 DAO。
 *
 * @author warm
 */
public class DefinitionMemDao<T extends Definition> extends InMemoryDao<T> implements FlowDefinitionDao<T> {

    public DefinitionMemDao(Supplier<T> factory, List<String> log) {
        super(factory, log);
    }

    @Override
    public List<T> queryByCodeList(List<String> codeList) {
        log.add(name() + ".queryByCodeList" + codeList);
        return store.values().stream()
                .filter(d -> codeList.contains(((Definition) d).getFlowCode()))
                .sorted(Comparator.comparing(Definition::getVersion).reversed())
                .map(this::copyOf)
                .collect(Collectors.toList());
    }

    @Override
    public void updatePublishStatus(List<Long> ids, Integer publishStatus) {
        log.add(name() + ".updatePublishStatus" + ids + ",status=" + publishStatus);
        for (Definition d : store.values().stream().map(Definition.class::cast).collect(Collectors.toList())) {
            if (ids.contains(d.getId())) {
                d.setIsPublish(publishStatus);
            }
        }
    }
}
